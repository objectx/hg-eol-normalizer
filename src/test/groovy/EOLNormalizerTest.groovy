import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by objectx on 2015/05/18.
 */
class EOLNormalizerTest extends Specification {
    @Unroll
    def "nextTabStop (#a) == #b" () {
    expect:
        EOLNormalizer.nextTabStop (a, b) == c
    where:
        a | b ||  c
        0 | 8 ||  8
        0 | 4 ||  4
        1 | 8 ||  8
        1 | 4 ||  4
        7 | 8 ||  8
        3 | 4 ||  4
        8 | 8 || 16
        4 | 4 ||  8
    }

    @Unroll
    def "expandTabs (#src, #tabstop) == #dst" () {
    expect:
        expandTabsHelper (src, tabstop) == dst
    where:
        src  | tabstop || dst
        ""   |    8    || ""
        ""   |    4    || ""
        " "  |    8    || " "
        "\t" |    8    || "        "
        "\t" |    4    || "    "
        " \t"|    8    || "        "
        "       \t"| 8 || "        "
        " \t\t"    | 8 || "                "
        " \t\t"    | 4 || "        "
    }

    private static final String expandTabsHelper (String src, int tabstop) {
        ByteArrayOutputStream tmp = new ByteArrayOutputStream ()
        final byte [] s = src.getBytes ()
        EOLNormalizer.expandTabs (tmp, s, 0, s.size (), tabstop)
        tmp.toString ()
    }
}
