CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name varchar(80) NOT NULL,
  email varchar(254) NOT NULL UNIQUE,
  password_hash varchar(100) NOT NULL,
  role varchar(10) NOT NULL DEFAULT 'USER' CHECK (role IN ('ADMIN','USER')),
  state varchar(10) NOT NULL DEFAULT 'ACTIVE' CHECK (state IN ('ACTIVE','INACTIVE')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE cases (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  case_number integer GENERATED ALWAYS AS IDENTITY UNIQUE,
  title varchar(120) NOT NULL,
  description text NOT NULL,
  location varchar(120) NOT NULL,
  incident_date timestamptz NOT NULL,
  status varchar(15) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','CLOSED')),
  image_url text,
  state varchar(10) NOT NULL DEFAULT 'ACTIVE' CHECK (state IN ('ACTIVE','INACTIVE')),
  owner_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE suspects (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  full_name varchar(120) NOT NULL,
  alias varchar(60),
  description text,
  suspicion_level integer NOT NULL DEFAULT 0 CHECK (suspicion_level BETWEEN 0 AND 100),
  status varchar(20) NOT NULL DEFAULT 'PERSON_OF_INTEREST' CHECK (status IN ('PERSON_OF_INTEREST','DETAINED','CLEARED')),
  photo_url text,
  state varchar(10) NOT NULL DEFAULT 'ACTIVE' CHECK (state IN ('ACTIVE','INACTIVE')),
  case_id uuid NOT NULL REFERENCES cases(id) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE evidences (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title varchar(120) NOT NULL,
  description text NOT NULL,
  type varchar(15) NOT NULL CHECK (type IN ('PHYSICAL','DIGITAL','DOCUMENT','TESTIMONY','FORENSIC')),
  collected_at timestamptz NOT NULL,
  relevance integer NOT NULL CHECK (relevance BETWEEN 1 AND 5),
  image_url text,
  state varchar(10) NOT NULL DEFAULT 'ACTIVE' CHECK (state IN ('ACTIVE','INACTIVE')),
  case_id uuid NOT NULL REFERENCES cases(id) ON DELETE RESTRICT,
  suspect_id uuid REFERENCES suspects(id) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_cases_owner ON cases(owner_id);
CREATE INDEX idx_cases_status ON cases(status);
CREATE INDEX idx_suspects_case ON suspects(case_id);
CREATE INDEX idx_evidences_case ON evidences(case_id);
CREATE INDEX idx_evidences_suspect ON evidences(suspect_id);
