package edu.investigacion.domain;

public final class Enums {
    private Enums() {}

    public enum Role {
        ADMIN,
        USER
    }

    public enum State {
        ACTIVE,
        INACTIVE
    }

    public enum CaseStatus {
        OPEN,
        IN_PROGRESS,
        CLOSED
    }

    public enum SuspectStatus {
        PERSON_OF_INTEREST,
        DETAINED,
        CLEARED
    }

    public enum EvidenceType {
        PHYSICAL,
        DIGITAL,
        DOCUMENT,
        TESTIMONY,
        FORENSIC
    }
}
