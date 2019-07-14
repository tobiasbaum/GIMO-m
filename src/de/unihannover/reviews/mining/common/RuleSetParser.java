package de.unihannover.reviews.mining.common;

import java.util.ArrayList;
import java.util.List;

public class RuleSetParser {

    public static final String DEFAULT_RULE = "normally use ";
    public static final String HEADER = "skip when one of";
    public static final String EXCLUSION_BREAK = "unless one of";

    private final RecordScheme scheme;


    public RuleSetParser(RecordScheme scheme) {
        this.scheme = scheme;
    }

    public RuleSet parse(String text) {
        final String[] lines = text.replace("\r\n", "\n").split("\n");
        boolean incl = true;

        if (!lines[0].startsWith(DEFAULT_RULE)) {
            throw new IllegalArgumentException("Syntax error: " + lines[0]);
        }
        final String defaultValue = lines[0].substring(DEFAULT_RULE.length()).trim();
        RuleSet ret = RuleSet.create(defaultValue);
        if (!lines[1].equals(HEADER)) {
            throw new IllegalArgumentException("Syntax error: " + lines[1]);
        }
        for (int i = 2; i < lines.length; i++) {
            final String lt = lines[i].trim();
            if (lt.equals(EXCLUSION_BREAK)) {
                incl = false;
            } else if (!lt.isEmpty()) {
                if (incl) {
                    ret = ret.include(this.parseRule(lt));
                } else {
                    ret = ret.exclude(this.parseRule(lt));
                }
            }
        }
        return ret;
    }

    public And parseRule(String lt) {
        final List<Rule> subrules = new ArrayList<>();
        for (final String part : this.splitAtAnd(lt)) {
            subrules.add(this.parseSimpleRule(part.trim()));
        }
        return new And(subrules.toArray(new Rule[subrules.size()]));
    }

    String[] splitAtAnd(String lt) {
        String content;
        if (lt.startsWith("or (")) {
            content = lt.substring(4);
        } else if (lt.startsWith("(")) {
            content = lt.substring(1);
        } else {
            throw new IllegalArgumentException("Syntax error: " + lt);
        }

        if (content.endsWith(")")) {
            content = content.substring(0, content.length() - 1);
        } else {
            throw new IllegalArgumentException("Syntax error: " + lt);
        }

        if (content.isEmpty()) {
            return new String[0];
        }

        //TODO: not really safe when there are ands in strings
        return content.split(" and ");
    }

    public SimpleRule parseSimpleRule(String r) {
        if (r.equals("true")) {
            return new True();
        }
        if (r.equals("false")) {
            return new False();
        }

        final int firstNonLetter = this.findFirstNonLetter(r);
        if (firstNonLetter < 0) {
            throw new IllegalArgumentException("Syntax error: " + r);
        }

        final String column = r.substring(0, firstNonLetter);
        final String rest = r.substring(firstNonLetter).trim();

        if (rest.startsWith("<=")) {
            final double val = Double.parseDouble(rest.substring(2).trim());
            return new Leq(this.scheme, this.scheme.getAbsIndex(column), val);
        } else if (rest.startsWith(">=")) {
            final double val = Double.parseDouble(rest.substring(2).trim());
            return new Geq(this.scheme, this.scheme.getAbsIndex(column), val);
        } else if (rest.startsWith("==")) {
            final String withoutBrackets = this.unescape(rest.substring(2).trim());
            return new Equals(this.scheme, this.scheme.getAbsIndex(column), withoutBrackets);
        } else if (rest.startsWith("!=")) {
            final String withoutBrackets = this.unescape(rest.substring(2).trim());
            return new NotEquals(this.scheme, this.scheme.getAbsIndex(column), withoutBrackets);
        } else {
            throw new IllegalArgumentException("Syntax error: " + rest);
        }
    }

    private String unescape(String trim) {
    	if (!trim.startsWith("'")) {
    		throw new IllegalArgumentException("string does not start with quote: " + trim);
    	}
    	if (!trim.endsWith("'")) {
    		throw new IllegalArgumentException("string does not end with quote: " + trim);
    	}
		return trim.substring(1, trim.length() - 1).replace("\\'", "'").replace("\\\\", "\\");
	}

	private int findFirstNonLetter(String r) {
        for (int i = 0; i < r.length(); i++) {
            if (!Character.isLetter(r.charAt(i)) && !Character.isDigit(r.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

}
