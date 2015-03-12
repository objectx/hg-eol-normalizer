import groovy.transform.CompileStatic
import java.nio.file.*
import java.util.UUID.*

def mercurial = "hg.exe"

def files = [mercurial, "files"].execute ()

@CompileStatic
boolean is_target (String f) {
    (f ==~ ~/.+\.(?:h|hh|hpp|hxx|h\+\+|c|cc|cpp|cxx|c\+\+)$/)
}

@CompileStatic
ByteArrayOutputStream eliminate_CRLF (byte [] input) {
    ByteArrayOutputStream output = new ByteArrayOutputStream ()
    int head = 0 ;
    int tail = 0 ;
    while (tail < input.length) {
        int ch = input [tail]
        if (ch == 0x0d) {
            if ((tail + 1) < input.length && input [tail + 1] == 0x0a) {
                // \r\n found
                output.write input, head, (tail - head)
                output.write 0x0a
                tail += 2
                head = tail
                continue
            }
            // '\r' alone
        }
        else if (ch == 0x0a) {
            output.write input, head, (tail - head + 1)
            tail++
            head = tail
            continue
        }
        tail++
    }
    if (head < tail) {
        output.write input, head, (tail - head)
    }
    if (0 < input.length && input [input.length - 1] != 0x0a) {
        output.write (0x0a)
    }
    output
}


@CompileStatic
def normalize_eol (Path path) {
    UUID uuid = UUID.randomUUID ()
    Path tmp = Paths.get path.parent.toString (), "cv-${uuid}.tmp"
    //println "Write to ${tmp}"
    byte [] bytes = Files.readAllBytes path
    ByteArrayOutputStream out = eliminate_CRLF bytes
    if (bytes.length != out.size ()) {
        println "bytes = ${bytes.length}"
        println "     -> ${out.size ()}"
        tmp.withOutputStream { o ->
            out.writeTo o
        }
        Files.move tmp, path, StandardCopyOption.REPLACE_EXISTING
    }
}


files.in.eachLine { l ->
    if (is_target (l)) {
        normalize_eol Paths.get (l)
    }
}
