package edu.cybertrace;
import org.junit.jupiter.api.Test;import java.nio.file.*;import static org.junit.jupiter.api.Assertions.*;
class HashingTest {
 @Test void unchangedFileHasSameHash()throws Exception{Path p=Files.createTempFile("evidence",".txt");Files.writeString(p,"original evidence");assertEquals(ForensicsService.hash(p),ForensicsService.hash(p));Files.delete(p);}
 @Test void changedBytesProduceMismatchWithoutChangingReference()throws Exception{Path p=Files.createTempFile("evidence",".txt");try{Files.writeString(p,"original evidence");String reference=ForensicsService.hash(p);Files.writeString(p,"modified evidence");String observed=ForensicsService.hash(p);assertNotEquals(reference,observed);assertEquals("modified evidence",Files.readString(p));assertNotEquals(reference,ForensicsService.hash(p));}finally{Files.deleteIfExists(p);}}
 @Test void emptyFileCanBeHashed()throws Exception{Path p=Files.createTempFile("empty",".txt");assertEquals(64,ForensicsService.hash(p).length());Files.delete(p);}
}
