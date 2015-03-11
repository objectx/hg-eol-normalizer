import groovy.transform.CompileStatic

def mercurial = "hg.exe"

def files = [mercurial, "files"].execute ()

@CompileStatic
boolean is_target (String f) {
    (f ==~ ~/.+\.(?:h|hh|hpp|hxx|h\+\+|c|cc|cpp|cxx|c\+\+)$/)
}

files.in.eachLine { l ->
    if (is_target (l)) {
        println l
    }
}
