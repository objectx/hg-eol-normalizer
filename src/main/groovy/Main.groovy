/* -*- mode: groovy, encoding: utf-8 -*-
 *
 * hg-eol-normalizer:
 *
 */
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovy.util.logging.Slf4j

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger

import java.nio.file.*
import java.util.UUID.*
import java.util.regex.Pattern
import java.util.regex.Matcher

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

def cli = build_option_parser ()

def options = cli.parse args

if (! options) {
    System.exit 1
}

if (options.'help') {
    cli.usage ()
    System.exit 1
}

@Field Logger rootLogger = LoggerFactory.getLogger org.slf4j.Logger.ROOT_LOGGER_NAME

if (options.'verbose') {
    rootLogger.level = Level.INFO
}

EOLNormalizer eol_normalizer = new EOLNormalizer (dryrun: options.'dry-run')

def repos = options.arguments ()

try {
    if (! repos) {
        eol_normalizer.normalize (Paths.get ('.'))
    }
    else {
        for (def r : repos) {
            eol_normalizer.normalize (Paths.get (r))
        }
    }
}
catch (Exception e) {
    rootLogger.error "Something wrong happen! ({})", e.message
    System.exit 1
}

System.exit 0

@Slf4j
class EOLNormalizer {
    boolean dryrun = false
    static final Pattern rxTarget = Pattern.compile (/.+\.(?:h|hh|hpp|hxx|h\+\+|c|cc|cpp|cxx|c\+\+|py|pl|java|groovy)$/)
    /**
     * Returns true when the file F is a conversion target
     *
     * @param  f filename to check
     * @return   true if F is target
     */
    @CompileStatic
    static final boolean isTarget (Path f) {
        Matcher m = rxTarget.matcher f.fileName.toString ()
        m.matches ()
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
    static final ByteArrayOutputStream eliminateCRLF (byte [] input) {
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
    def normalizeEOL (Path path) {
        log.info "target: {}", path.toString ()
        UUID uuid = UUID.randomUUID ()
        Path tmp

        if (path.parent) {
            tmp = path.parent.resolve "cv-${uuid}.tmp"
        }
        else {
            tmp = Paths.get "cv-${uuid}.tmp"
        }

        byte [] bytes = Files.readAllBytes path
        ByteArrayOutputStream out = eliminateCRLF bytes
        // This code assumes that eliminate_CRLF always shorten the size (if conversions occur)
        if (bytes.length != out.size ()) {
            log.info "Conversion occur ({} bytes -> {} bytes)", bytes.length, out.size ()
            if (! dryrun) {
                log.info "Write to: {}", tmp.toString ()
                tmp.withOutputStream { o ->
                    out.writeTo o
                }
                log.info "Rename {} to {}", tmp.toString (), path.toString ()
                Files.move tmp, path, StandardCopyOption.REPLACE_EXISTING
            }
        }
    }

    @CompileStatic
    def normalize (Path repo) {
        Process files

        if (Files.exists (repo.resolve ('.hg'))) {
            log.info ".hg/ found"
            files = ["hg", "files"].execute ([], repo.toFile ())
        }
        else if (Files.exists (repo.resolve ('.git'))) {
            log.info ".git/ found"
            files = ["git", "ls-files"].execute ([], repo.toFile ())
        }
        else {
            throw new IOException ("${repo} is neither git nor mercurial repository.")
        }

        files.in.eachLine { l ->
            Path target = repo.resolve l
            if (isTarget (target)) {
                normalizeEOL target
            }
        }
    }
}
