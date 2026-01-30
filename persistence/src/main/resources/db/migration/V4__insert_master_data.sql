-- ============================================
-- 긴급도 타입 기본 데이터
-- ============================================

INSERT INTO urgency_type (name, level) VALUES
    ('정보', 1),
    ('주의', 3),
    ('경계', 5),
    ('심각', 7),
    ('긴급', 9);

-- ============================================
-- 사건 유형 기본 데이터
-- ============================================

INSERT INTO incident_type (code, name) VALUES
    ('AVIAN_INFLUENZA', '조류독감'),
    ('DROUGHT', '가뭄'),
    ('LIVESTOCK_DISEASE', '가축질병'),
    ('STRONG_WIND', '강풍'),
    ('DRY_WEATHER', '건조'),
    ('TRAFFIC', '교통'),
    ('TRAFFIC_ACCIDENT', '교통사고'),
    ('TRAFFIC_CONTROL', '교통통제'),
    ('FINANCE', '금융'),
    ('OTHER', '기타'),
    ('HEAVY_SNOW', '대설'),
    ('FINE_DUST', '미세먼지'),
    ('CIVIL_DEFENSE', '민방공'),
    ('COLLAPSE', '붕괴'),
    ('FOREST_FIRE', '산불'),
    ('LANDSLIDE', '산사태'),
    ('WATER_SUPPLY', '수도'),
    ('FOG', '안개'),
    ('ENERGY', '에너지'),
    ('EPIDEMIC', '전염병'),
    ('POWER_OUTAGE', '정전'),
    ('EARTHQUAKE', '지진'),
    ('TSUNAMI', '지진해일'),
    ('TYPHOON', '태풍'),
    ('TERRORISM', '테러'),
    ('COMMUNICATION', '통신'),
    ('EXPLOSION', '폭발'),
    ('HEAT_WAVE', '폭염'),
    ('HIGH_SEAS', '풍랑'),
    ('COLD_WAVE', '한파'),
    ('HEAVY_RAIN', '호우'),
    ('FLOOD', '홍수'),
    ('FIRE', '화재'),
    ('ENVIRONMENTAL_POLLUTION', '환경오염사고'),
    ('YELLOW_DUST', '황사'),
    ('MARITIME_ACCIDENT', '해양선박사고'),
    ('DEATH', '사망');
