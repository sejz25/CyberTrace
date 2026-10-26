package edu.cybertrace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface Users extends JpaRepository<UserRecord,Long>{Optional<UserRecord> findByUsername(String username);boolean existsByRole(String role);}
interface EvidenceRepo extends JpaRepository<Evidence,Long>{Optional<Evidence> findByEvidenceNo(String no);}
interface CustodyRepo extends JpaRepository<Custody,Long>{List<Custody> findByEvidenceIdOrderByIdAsc(Long id);}
interface VerificationRepo extends JpaRepository<Verification,Long>{List<Verification> findAllByOrderByIdDesc();}
interface AuditRepo extends JpaRepository<AuditEntry,Long>{List<AuditEntry> findAllByOrderByIdAsc();Optional<AuditEntry> findTopByOrderByIdDesc();}
interface AlertRepo extends JpaRepository<AlertRecord,Long>{List<AlertRecord> findAllByOrderByIdDesc();}
interface CaseRepo extends JpaRepository<CaseRecord,Long>{Optional<CaseRecord> findByCaseNo(String caseNo);}
interface CaseShareRepo extends JpaRepository<CaseShare,Long>{List<CaseShare> findByUsername(String username);boolean existsByCaseIdAndUsername(Long caseId,String username);}
interface EvidenceVersionRepo extends JpaRepository<EvidenceVersion,Long>{List<EvidenceVersion> findByEvidenceIdOrderByVersionNumberDesc(Long evidenceId);}
interface CaseNoteRepo extends JpaRepository<CaseNote,Long>{List<CaseNote> findByCaseIdOrderByIdAsc(Long caseId);}
interface SystemSettingRepo extends JpaRepository<SystemSetting,String>{}
