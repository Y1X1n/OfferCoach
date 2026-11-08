-- 公开部署合规：移除非宽松许可来源的题库（UNLICENSED / CC-BY-NC-SA / CC-BY-NC-ND / GPL-3.0）。
-- 来源清单与依据见 data/question-bank/LICENSES.md；导入器已同步排除这些仓库。
DELETE FROM question_bank
WHERE source_repo IN (
    'DerekLin924/bat_code',
    '0voice/Campus_recruitment_interview_questions',
    'ByteByteGoHq/system-design-101',
    'ayush-that/codejeet'
);