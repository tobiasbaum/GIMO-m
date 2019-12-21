/**
 * Copyright 2019 Tobias Baum
 *
 * This file is part of GIMO-m.
 *
 * GIMO-m is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GIMO-m is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package de.unihannover.gimo_m.mining.common;

import java.util.ArrayList;
import java.util.List;

public class RuleSetParser {

    public static final String DEFAULT_RULE = "normally use ";
    public static final String EXCEPT_RULE = "but use ";
    public static final String EXCEPT_RULE_SUFFIX = " when";

    private final RecordScheme scheme;


    public RuleSetParser(RecordScheme scheme) {
        this.scheme = scheme;
    }

    public RuleSet parse(String text) {
        final String[] lines = text.replace("\r\n", "\n").split("\n");

        if (!lines[0].startsWith(DEFAULT_RULE)) {
            throw new IllegalArgumentException("Syntax error: " + lines[0]);
        }
        final String defaultValue = lines[0].substring(DEFAULT_RULE.length()).trim();
        RuleSet rs = RuleSet.create(defaultValue);
        if (lines.length == 1) {
            return rs;
        }
        String curClass = null;
        Or cur = null;
        for (int i = 1; i < lines.length; i++) {
            if (curClass == null && !lines[i].startsWith(EXCEPT_RULE)) {
                throw new IllegalArgumentException("Syntax error: " + lines[i]);
            }
            if (lines[i].startsWith(EXCEPT_RULE)) {
                if (curClass != null) {
                    rs = rs.addException(curClass, cur);
                }
                if (!lines[i].endsWith(EXCEPT_RULE_SUFFIX)) {
                    throw new IllegalArgumentException("Syntax error: " + lines[i]);
                }
                curClass = extractClassificationFromExtractRule(lines[i]);
                cur = new Or();
            } else {
                final String lt = lines[i].trim();
                cur = cur.or(this.parseRule(lt));
            }
        }
        if (cur != null) {
            rs = rs.addException(curClass, cur);
            cur = new Or();
        }
        return rs;
    }

    public static String extractClassificationFromExtractRule(final String line) {
        return line.trim().substring(EXCEPT_RULE.length(), line.length() - EXCEPT_RULE_SUFFIX.length()).trim();
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
            final char ch = r.charAt(i);
            if (!Character.isLetter(ch) && !Character.isDigit(ch) && ch != '.' && ch != '_') {
                return i;
            }
        }
        return -1;
    }

}
