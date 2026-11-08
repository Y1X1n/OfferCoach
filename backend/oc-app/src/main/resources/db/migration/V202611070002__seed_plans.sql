INSERT INTO subscription_plan
    (code, name, price_cents, period_days, interview_credits, resume_credits, features, active)
VALUES
    ('FREE', '免费版', 0, 30, 3, 3,
     '{"rewrittenAnswer": false, "pdfExport": false, "priorityQueue": false, "unlimitedResume": false}'::jsonb,
     true),
    ('PRO', '专业版', 3900, 30, 30, 9999,
     '{"rewrittenAnswer": true, "pdfExport": false, "priorityQueue": true, "unlimitedResume": true}'::jsonb,
     true)
ON CONFLICT (code) DO NOTHING;