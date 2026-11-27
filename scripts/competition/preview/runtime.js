/**
 * 极简小程序运行时：把 miniprogram/ 里的 axml / acss / js 直接跑在浏览器里。
 *
 * 目标不是 100% 复刻支付宝引擎，而是让真实页面代码（Page/setData/事件/模板绑定）
 * 与真实后端联调，用于本地预览与录制演示。差异点集中在本文件，页面代码不做任何适配。
 */
(function () {
  const PREVIEW_VERSION = '1.0.0';
  const PAGE_EL = document.getElementById('page');
  const TOAST_EL = document.getElementById('toast');
  const LOADING_EL = document.getElementById('loading');
  const TITLE_EL = document.getElementById('nav-title');
  const BACK_EL = document.getElementById('nav-back');

  const RPX_RATIO = 390 / 750;
  const TAG_MAP = {
    view: 'div',
    text: 'span',
    block: 'fragment',
    button: 'button',
    textarea: 'textarea',
    input: 'input',
    image: 'img',
    scrollview: 'div',
    'scroll-view': 'div'
  };
  const BOOLEAN_ATTRS = ['disabled', 'checked', 'selected'];

  const state = {
    route: '',
    def: null,
    data: {},
    stack: [],
    template: null,
    query: {},
    toastTimer: null
  };

  const modules = {};

  // ------------------------------------------------------------------ 基础工具
  function fetchText(url) {
    return fetch(url, { cache: 'no-store' }).then(function (response) {
      if (!response.ok) {
        throw new Error('加载失败 ' + url + ' (' + response.status + ')');
      }
      return response.text();
    });
  }

  function normalizePath(basePath, spec) {
    const dir = basePath.slice(0, basePath.lastIndexOf('/') + 1);
    const parts = (dir + spec).split('/');
    const out = [];
    parts.forEach(function (part) {
      if (!part || part === '.') return;
      if (part === '..') {
        out.pop();
        return;
      }
      out.push(part);
    });
    let path = '/' + out.join('/');
    if (!/\.js$/.test(path)) {
      path += '.js';
    }
    return path;
  }

  function evalModule(path) {
    const entry = modules[path];
    if (!entry) {
      throw new Error('模块未预加载：' + path);
    }
    if (entry.loaded) {
      return entry.exports;
    }
    entry.loaded = true;
    const module = { exports: {} };
    const fn = new Function('require', 'module', 'exports', entry.source + '\n//# sourceURL=' + path);
    fn(
      function require(spec) {
        return evalModule(normalizePath(path, spec));
      },
      module,
      module.exports
    );
    entry.exports = module.exports;
    return module.exports;
  }

  function preload(paths) {
    return Promise.all(
      paths.map(function (path) {
        if (modules[path]) return Promise.resolve();
        return fetchText(path).then(function (source) {
          modules[path] = { source: source, exports: {}, loaded: false };
        });
      })
    );
  }

  // ------------------------------------------------------------------ my.* 兼容层
  const storagePrefix = 'oc-preview:';

  const my = {
    request: function (options) {
      const opts = options || {};
      const url = new URL(opts.url, window.location.href);
      const target = url.pathname + url.search;
      const init = {
        method: opts.method || 'GET',
        headers: opts.headers || {}
      };
      if (opts.data !== undefined && opts.data !== null) {
        init.body = JSON.stringify(opts.data);
      }
      const started = Date.now();
      window.__previewLog && window.__previewLog('request', opts.method || 'GET', target);
      fetch(target, init)
        .then(function (response) {
          return response
            .json()
            .catch(function () {
              return null;
            })
            .then(function (body) {
              window.__previewLog &&
                window.__previewLog('response', response.status, target, Date.now() - started + 'ms');
              opts.success && opts.success({ data: body, status: response.status, headers: {} });
              opts.complete && opts.complete();
            });
        })
        .catch(function (error) {
          window.__previewLog && window.__previewLog('error', target, error.message);
          opts.fail && opts.fail({ error: 19, errorMessage: error.message });
          opts.complete && opts.complete();
        });
    },

    getStorageSync: function (options) {
      const key = options && options.key;
      const raw = window.localStorage.getItem(storagePrefix + key);
      let data = null;
      if (raw !== null) {
        try {
          data = JSON.parse(raw);
        } catch (error) {
          data = raw;
        }
      }
      return { data: data };
    },

    setStorageSync: function (options) {
      const key = options && options.key;
      window.localStorage.setItem(storagePrefix + key, JSON.stringify(options.data));
      return { data: options.data };
    },

    removeStorageSync: function (options) {
      window.localStorage.removeItem(storagePrefix + (options && options.key));
    },

    showToast: function (options) {
      const opts = options || {};
      TOAST_EL.textContent = opts.content || opts.title || '';
      TOAST_EL.className = 'toast show';
      clearTimeout(state.toastTimer);
      state.toastTimer = setTimeout(function () {
        TOAST_EL.className = 'toast';
      }, opts.duration || 2000);
    },

    showLoading: function () {
      LOADING_EL.classList.remove('hidden');
    },

    hideLoading: function () {
      LOADING_EL.classList.add('hidden');
    },

    navigateTo: function (options) {
      navigate(options.url, 'push');
    },

    redirectTo: function (options) {
      navigate(options.url, 'replace');
    },

    reLaunch: function (options) {
      navigate(options.url, 'relaunch');
    },

    navigateBack: function () {
      if (state.stack.length > 1) {
        state.stack.pop();
        const previous = state.stack[state.stack.length - 1];
        boot(previous.route, previous.query);
      }
    },

    switchTab: function (options) {
      navigate(options.url, 'relaunch');
    },

    pageScrollTo: function (options) {
      PAGE_EL.scrollTo({ top: (options && options.scrollTop) || 0, behavior: 'smooth' });
    },

    getSystemInfoSync: function () {
      return { windowWidth: 390, windowHeight: 756, platform: 'devtools', brand: 'preview' };
    },

    canIUse: function () {
      return true;
    }
  };

  window.my = my;
  window.getApp = function () {
    return { globalData: {} };
  };

  // ------------------------------------------------------------------ 模板解析与渲染
  function parseTemplate(axml) {
    const escaped = axml
      .replace(/a:else(?![=\w])/g, 'a:else=""')
      .replace(/&(?!#?\w+;)/g, '&amp;')
      .replace(/<\s*(textarea|scroll-view|button|image|input|block|view|text)(?=[\s/>])([^>]*?)\s*\/>/g, function (_, tag, attrs) {
        return '<' + tag + attrs + '></' + tag + '>';
      });
    const doc = new DOMParser().parseFromString(
      '<root xmlns:a="urn:axml">' + escaped + '</root>',
      'application/xml'
    );
    const error = doc.querySelector('parsererror');
    if (error) {
      throw new Error('axml 解析失败：' + error.textContent.slice(0, 200));
    }
    return doc.documentElement;
  }

  function directive(raw) {
    const matched = /^\s*\{\{([\s\S]*)\}\}\s*$/.exec(raw || '');
    return matched ? matched[1] : raw || '';
  }

  function evaluate(expression, ctx) {
    try {
      const fn = new Function('ctx', 'with (ctx) { return (' + expression + '); }');
      return fn(ctx);
    } catch (error) {
      window.__previewLog && window.__previewLog('expr-error', expression, error.message);
      return undefined;
    }
  }

  function interpolate(text, ctx) {
    if (!text || text.indexOf('{{') === -1) {
      return text;
    }
    return text.replace(/\{\{([\s\S]+?)\}\}/g, function (_, expression) {
      const value = evaluate(expression, ctx);
      return value === undefined || value === null ? '' : String(value);
    });
  }

  function toPx(value) {
    return String(value).replace(/(-?\d+(?:\.\d+)?)rpx/g, function (_, number) {
      return parseFloat(number) * RPX_RATIO + 'px';
    });
  }

  function renderChildren(node, ctx, out, path) {
    let previousCondition = null;
    let index = 0;
    Array.prototype.forEach.call(node.childNodes, function (child) {
      if (child.nodeType === 3) {
        const text = interpolate(child.nodeValue, ctx);
        if (text.trim()) {
          out.appendChild(document.createTextNode(text));
        }
        return;
      }
      if (child.nodeType !== 1) {
        return;
      }
      index += 1;
      const el = child;

      if (el.hasAttribute('a:for')) {
        const list = evaluate(directive(el.getAttribute('a:for')), ctx) || [];
        Array.prototype.forEach.call(list, function (item, itemIndex) {
          const childCtx = Object.create(ctx);
          childCtx.item = item;
          childCtx.index = itemIndex;
          renderElement(el, childCtx, out, path + '.' + index + '.' + itemIndex);
        });
        previousCondition = null;
        return;
      }

      if (el.hasAttribute('a:if')) {
        previousCondition = !!evaluate(directive(el.getAttribute('a:if')), ctx);
        if (previousCondition) {
          renderElement(el, ctx, out, path + '.' + index);
        }
        return;
      }

      if (el.hasAttribute('a:elif')) {
        if (!previousCondition && evaluate(directive(el.getAttribute('a:elif')), ctx)) {
          previousCondition = true;
          renderElement(el, ctx, out, path + '.' + index);
        }
        return;
      }

      if (el.hasAttribute('a:else')) {
        if (!previousCondition) {
          renderElement(el, ctx, out, path + '.' + index);
        }
        previousCondition = null;
        return;
      }

      previousCondition = null;
      renderElement(el, ctx, out, path + '.' + index);
    });
  }

  function renderElement(el, ctx, out, path) {
    const tag = el.tagName.toLowerCase();
    if (tag === 'block' || tag === 'template') {
      renderChildren(el, ctx, out, path);
      return;
    }

    const dom = document.createElement(TAG_MAP[tag] || 'div');
    dom.setAttribute('data-nid', path);

    const dataset = {};
    Array.prototype.forEach.call(el.attributes, function (attribute) {
      const name = attribute.name;
      if (name.indexOf('a:') === 0) {
        return;
      }
      if (name === 'class') {
        dom.className = interpolate(attribute.value, ctx);
        return;
      }
      if (name === 'style') {
        dom.setAttribute('style', toPx(interpolate(attribute.value, ctx)));
        return;
      }
      if (name === 'onTap') {
        dom.addEventListener('click', function (event) {
          event.stopPropagation();
          invoke(attribute.value, el, dataset);
        });
        return;
      }
      if (name === 'onInput') {
        dom.addEventListener('input', function (event) {
          invoke(attribute.value, el, dataset, { value: event.target.value });
        });
        return;
      }
      if (name === 'onConfirm') {
        dom.addEventListener('keydown', function (event) {
          if (event.key === 'Enter') {
            invoke(attribute.value, el, dataset, { value: event.target.value });
          }
        });
        return;
      }
      if (name.indexOf('data-') === 0) {
        dataset[name.slice(5).replace(/-([a-z])/g, function (_, char) {
          return char.toUpperCase();
        })] = interpolate(attribute.value, ctx);
        return;
      }
      const value = interpolate(attribute.value, ctx);
      if (BOOLEAN_ATTRS.indexOf(name) >= 0) {
        dom[name] = value === 'true' || value === '1';
        return;
      }
      if (name === 'value' && (tag === 'textarea' || tag === 'input')) {
        dom.value = value;
        return;
      }
      if (name === 'src' && tag === 'image') {
        dom.setAttribute('src', value);
        return;
      }
      dom.setAttribute(name, value);
    });


    if (tag === 'textarea') {
      dom.value = interpolate(el.getAttribute('value') || '', ctx);
    }

    renderChildren(el, ctx, dom, path);
    out.appendChild(dom);
  }

  function invoke(handlerName, el, dataset, detail) {
    const def = state.def;
    if (!def || typeof def[handlerName] !== 'function') {
      return;
    }
    def[handlerName].call(def, {
      type: 'tap',
      currentTarget: { dataset: dataset },
      target: { dataset: dataset },
      detail: detail || {}
    });
  }

  function render() {
    if (!state.template) {
      return;
    }
    const scrollTop = PAGE_EL.scrollTop;
    const active = document.activeElement;
    const nid = active && active.getAttribute ? active.getAttribute('data-nid') : null;
    const selectionStart = active && typeof active.selectionStart === 'number' ? active.selectionStart : null;
    const selectionEnd = active && typeof active.selectionEnd === 'number' ? active.selectionEnd : null;

    const frag = document.createDocumentFragment();
    renderChildren(state.template, state.data, frag, '0');
    PAGE_EL.innerHTML = '';
    PAGE_EL.appendChild(frag);
    PAGE_EL.scrollTop = scrollTop;

    if (nid) {
      const focused = PAGE_EL.querySelector('[data-nid="' + nid + '"]');
      if (focused) {
        focused.focus();
        if (selectionStart !== null && focused.setSelectionRange) {
          try {
            focused.setSelectionRange(selectionStart, selectionEnd);
          } catch (error) {
            /* 非文本控件忽略 */
          }
        }
      }
    }
  }

  // ------------------------------------------------------------------ 样式与页面装载
  function installCss(acss, id) {
    const styleId = 'oc-style-' + id;
    let styleEl = document.getElementById(styleId);
    if (!styleEl) {
      styleEl = document.createElement('style');
      styleEl.id = styleId;
      document.head.appendChild(styleEl);
    }
    const css = acss
      .replace(/(^|\})\s*page\s*\{/g, '$1 #page {')
      .replace(/env\(safe-area-inset-bottom\)/g, '0px')
      .replace(/(-?\d+(?:\.\d+)?)rpx/g, function (_, number) {
        return parseFloat(number) * RPX_RATIO + 'px';
      });
    styleEl.textContent = css;
  }

  function loadPageDefinition(base) {
    let captured = null;
    window.Page = function (definition) {
      captured = definition;
    };
    evalModule(base + '.js');
    if (!captured) {
      throw new Error('页面未调用 Page()：' + base);
    }
    return captured;
  }

  function parseRoute(url) {
    const [pathname, search] = url.split('?');
    const query = {};
    (search || '').split('&').forEach(function (pair) {
      if (!pair) return;
      const [key, value] = pair.split('=');
      query[key] = decodeURIComponent(value || '');
    });
    return { route: pathname.replace(/^\//, ''), query: query };
  }

  function navigate(url, mode) {
    const parsed = parseRoute(url);
    if (mode === 'replace' && state.stack.length) {
      state.stack[state.stack.length - 1] = parsed;
    } else if (mode === 'relaunch') {
      state.stack = [parsed];
    } else {
      state.stack.push(parsed);
    }
    boot(parsed.route, parsed.query);
  }

  function updateNav(title) {
    TITLE_EL.textContent = title || 'OfferCoach';
    BACK_EL.classList.toggle('hidden', state.stack.length <= 1);
  }

  function boot(route, query) {
    state.route = route;
    state.query = query || {};
    const base = '/miniprogram/' + route;

    return Promise.all([
      fetchText(base + '.axml'),
      fetchText(base + '.acss').catch(function () {
        return '';
      }),
      fetchText(base + '.json').catch(function () {
        return '{}';
      }),
      fetchText('/miniprogram/app.acss').catch(function () {
        return '';
      })
    ])
      .then(function (files) {
        return preload([
          '/miniprogram/utils/config.js',
          '/miniprogram/utils/format.js',
          '/miniprogram/utils/api.js',
          base + '.js'
        ]).then(function () {
          return files;
        });
      })
      .then(function (files) {
        const axml = files[0];
        const pageAcss = files[1];
        let pageJson = {};
        try {
          pageJson = JSON.parse(files[2]);
        } catch (error) {
          pageJson = {};
        }
        const appAcss = files[3];

        // 预览环境：把 API 基址换成本地代理，并按需注入体验码。
        const config = evalModule('/miniprogram/utils/config.js');
        config.API_BASE = '/api/v1';
        const urlParams = new URLSearchParams(window.location.search);
        if (urlParams.get('code')) {
          config.ACCESS_CODE = urlParams.get('code');
        }

        installCss(appAcss, 'app');
        installCss(pageAcss, route.replace(/\//g, '-'));

        state.template = parseTemplate(axml);
        const definition = loadPageDefinition(base);
        state.def = definition;
        state.data = definition.data ? JSON.parse(JSON.stringify(definition.data)) : {};
        definition.data = state.data;
        definition.setData = function (patch, callback) {
          state.data = Object.assign({}, state.data, patch);
          definition.data = state.data;
          render();
          if (callback) callback();
        };
        definition.selectComponent = function () {
          return null;
        };

        updateNav(pageJson.defaultTitle);
        if (definition.onLoad) definition.onLoad(state.query);
        if (definition.onShow) definition.onShow();
        render();
        document.title = (pageJson.defaultTitle || 'OfferCoach') + ' · 预览';
        window.__previewState = { route: route, data: state.data, stack: state.stack };
      })
      .catch(function (error) {
        PAGE_EL.innerHTML =
          '<div style="padding:24px;font-size:13px;color:#b91c1c">预览启动失败：' + error.message + '</div>';
        throw error;
      });
  }

  // ------------------------------------------------------------------ 启动
  const params = new URLSearchParams(window.location.search);
  const initialRoute = params.get('page') || 'pages/index/index';
  state.stack = [{ route: initialRoute.replace(/^\//, ''), query: {} }];
  BACK_EL.addEventListener('click', function () {
    my.navigateBack();
  });

  window.__preview = {
    version: PREVIEW_VERSION,
    state: state,
    boot: boot,
    navigate: navigate
  };

  boot(initialRoute.replace(/^\//, ''), {})
    .then(function () {
      window.__previewReady = true;
      window.__previewLog && window.__previewLog('ready', initialRoute);
    })
    .catch(function (error) {
      window.__previewError = error.message;
      console.error(error);
    });
})();
