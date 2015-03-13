import groovy.transform.CompileStatic
import groovy.transform.Field
import java.nio.file.*
import java.util.UUID.*

def build_option_parser () {
    def scriptname = (new File (getClass().protectionDomain.codeSource.location.file)).name
    def cli = new CliBuilder (usage: "${scriptname} [options] [<repository>...]", stopAtNonOption: false)
    cli.with {
        H (longOpt: 'help', "Show this.")
        v (longOpt: 'verbose', "Be verbose.")
        N (longOpt: 'dry-run', "Don't modify anything.")
    }
    cli
}

@Field boolean verbose = false
@Field boolean dryrun = false

def cli = build_option_parser ()

def options = cli.parse args

if (! options) {
    System.exit 1
}

if (options.'help') {
    cli.usage ()
    System.exit 1
}

if (options.'verbose') {
    verbose = true
}

if (options.'dry-run') {
    dryrun = true
}

def repos = options.arguments ()

if (! repos) {
    do_normalize (Paths.get ('.'))
}
else {
    for (def r : repos) {
        do_normalize (Paths.get (r))
    }
}

System.exit 0

/**
 * Returns true when the file F is a conversion target
 *
 * @param  f filename to check
 * @return   true if F is target
 */
@CompileStatic
boolean is_target (Path f) {
    (f.fileName ==~ ~/.+\.(?:h|hh|hpp|hxx|h\+\+|c|cc|cpp|cxx|c\+\+)$/)
}

/**
 * Converts input \r\n sequence to \r.
 *
 * @param  input The input bytes
 * @return       Converted byte stream
 *
 * Note: We can't use File.eachLine method in this case (eachLine is encoding sensitive)
 */
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

/**
 * Normalizes EOL (to unix one)
 *
 * @param  path file to check
 */
@CompileStatic
def normalize_eol (Path path) {
    UUID uuid = UUID.randomUUID ()
    Path tmp = Paths.get path.parent.toString (), "cv-${uuid}.tmp"

    // System.err.println "Write to ${tmp}"

    byte [] bytes = Files.readAllBytes path
    ByteArrayOutputStream out = eliminate_CRLF bytes
    if (bytes.length != out.size ()) {
        if (verbose) {
            System.err.println "bytes = ${bytes.length}"
            System.err.println "     -> ${out.size ()}"
        }
        if (! dryrun) {
            tmp.withOutputStream { o ->
                out.writeTo o
            }
            Files.move tmp, path, StandardCopyOption.REPLACE_EXISTING
        }
    }
}

@CompileStatic
def do_normalize (Path repo) {
    //println "Dryrun = ${dryrun}"
    Process files = ["hg.exe", "files"].execute ([], repo.toFile ())

    files.in.eachLine { l ->
        Path target = Paths.get (repo.toString (), l)
        if (is_target (target)) {
            normalize_eol target
        }
    }
}
