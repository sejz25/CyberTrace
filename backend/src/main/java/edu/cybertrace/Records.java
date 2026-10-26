package edu.cybertrace;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name="users") class UserRecord {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(unique=true,nullable=false) public String username;
 @Column(nullable=false) public String password;
 @Column(nullable=false) public String role="INVESTIGATOR";
 @Column(nullable=false,columnDefinition="boolean default true") public boolean enabled=true;
 public UserRecord(){} UserRecord(String u,String p,String r){username=u;password=p;role=r;}
}
@Entity @Table(name="evidence") class Evidence {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(unique=true) public String evidenceNo;
 public String filename, storageName, category, description, caseNo, investigationRef, source, reason, registeredBy, custodian, mimeType;
 public long size;
 @Column(length=64,nullable=false,updatable=false) public String referenceHash;
 @Column(length=64) public String lastObservedHash;
 public String lastIntegrityStatus;
 public Instant lastCheckedAt;
 public Instant collectedAt, uploadedAt;
 public Evidence(){}
}
@Entity @Table(name="custody") class Custody {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Long evidenceId; public String previousCustodian, recipient, initiatedBy, reason, notes, status, resultingCustodian, fileHash; public int evidenceVersion;
 public Instant requestedAt, respondedAt;
 public Custody(){}
}
@Entity @Table(name="verifications") class Verification {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Long evidenceId; public String actor, referenceHash, currentVersionHash, observedHash, status; public Instant createdAt;
 public Verification(){}
}
@Entity @Table(name="audits") class AuditEntry {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Instant at; public String actor, action, target, outcome, details;
 @Column(length=64) public String previousHash;
 @Column(length=64) public String entryHash;
 public AuditEntry(){}
}
@Entity @Table(name="alerts") class AlertRecord {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public String severity, reason, actor, owner, acknowledgedBy; public Long evidenceId; public Instant createdAt; public boolean unread=true;
 public AlertRecord(){}
}
@Entity @Table(name="cases") class CaseRecord {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(unique=true,nullable=false) public String caseNo;
 public String title, description, createdBy, assignedTo, status;
 public LocalDate hearingDate, investigationDueDate;
 public Instant createdAt;
 public CaseRecord(){}
}
@Entity @Table(name="case_notes") class CaseNote {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Long caseId;
 public String author, authorRole, noteType, body;
 public Instant createdAt;
 public CaseNote(){}
}
@Entity @Table(name="system_settings") class SystemSetting {
 @Id public String settingKey;
 @Column(nullable=false) public String settingValue;
 public SystemSetting(){}
 public SystemSetting(String key,String value){settingKey=key;settingValue=value;}
}
@Entity @Table(name="case_shares") class CaseShare {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Long caseId; public String username, sharedBy; public Instant sharedAt;
 public CaseShare(){}
}
@Entity @Table(name="evidence_versions") class EvidenceVersion {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Long evidenceId; public int versionNumber; public String storageName, sha256, filename, changedBy, note; public Instant createdAt;
 public EvidenceVersion(){}
}
