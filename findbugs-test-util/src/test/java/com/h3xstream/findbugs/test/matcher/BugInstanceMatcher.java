/**
 * Find Security Bugs
 * Copyright (c) Philippe Arteau, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.h3xstream.findbugs.test.matcher;

import edu.umd.cs.findbugs.*;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BugInstanceMatcher extends BaseMatcher<BugInstance> {

    private static final Logger log = LoggerFactory.getLogger(BugInstanceMatcherBuilder.class);

    private static final Pattern ANON_FUNCTION_SCALA_PATTERN = Pattern.compile("\\$\\$anonfun\\$([^\\$]+)\\$");

    private String bugType;
    private String className;
    private String methodName;
    private String fieldName;
    private Integer lineNumber;
    private Integer lineNumberApprox;
    private String priority;
    private String jspFile;
    private List<Integer> multipleChoicesLine;

    /**
     * All the parameters are optional. Only the non-null parameters are used.
     *
     * @param bugType    Expected bug type
     * @param className  Class name
     * @param methodName Method name
     * @param fieldName  Field name
     * @param lineNumber Line number
     * @param lineNumberApprox Approximate line for test samples that are unstable (Historically the JSP samples)
     * @param priority   Priority
     * @param jspFile JSP file name
     * @param multipleChoicesLine At least of the line (JSP samples specific)
     */
    public BugInstanceMatcher(String bugType, String className, String methodName, String fieldName, Integer lineNumber, Integer lineNumberApprox, String priority, String jspFile, List<Integer> multipleChoicesLine) {
        this.bugType = bugType;
        this.className = className;
        this.methodName = methodName;
        this.fieldName = fieldName;
        this.lineNumber = lineNumber;
        this.lineNumberApprox = lineNumberApprox;
        this.priority = priority;
        this.jspFile = jspFile;
        this.multipleChoicesLine = multipleChoicesLine;
    }

    @Override
    public boolean matches(Object obj) {
        if (obj instanceof BugInstance) {
            BugInstance bugInstance = (BugInstance) obj;

            boolean criteriaMatches = true;
            if (bugType != null) {
                criteriaMatches &= bugInstance.getType().equals(bugType);
            }
            if (priority != null) {
                criteriaMatches &= bugInstance.getPriorityString().equals(priority);
            }
            if (className != null) {
                ClassAnnotation classAnn = extractBugAnnotation(bugInstance, ClassAnnotation.class);
                if (classAnn == null) return false;

                String fullName = classAnn.getClassName();
                int startDot = fullName.lastIndexOf(".") + 1;
                int endDollar = fullName.indexOf('$');
                String simpleName = fullName.substring(startDot != -1 ? startDot : 0, endDollar != -1 ? endDollar : fullName.length());
                String simpleNameInner = fullName.substring(startDot != -1 ? startDot : 0, fullName.length());
                criteriaMatches &= fullName.equals(className) || simpleName.equals(className) || simpleNameInner.equals(className);
            }
            if (methodName != null) {
                MethodAnnotation methodAnn = extractBugAnnotation(bugInstance, MethodAnnotation.class);
                ClassAnnotation classAnn = extractBugAnnotation(bugInstance, ClassAnnotation.class);
                String fullClassName = classAnn.getClassName();
                if (methodAnn == null) return false;

                if(methodAnn.getMethodName().startsWith("apply") && fullClassName != null) {
                    Matcher m = ANON_FUNCTION_SCALA_PATTERN.matcher(fullClassName);
                    if(m.find()) { //Scala function enclose in
                        criteriaMatches &= methodAnn.getMethodName().equals(methodName) || methodName.equals(m.group(1));
                    }
                }
                else { //
                    criteriaMatches &= methodAnn.getMethodName().equals(methodName);
                }
            }
            if (fieldName != null) {
                FieldAnnotation fieldAnn = extractBugAnnotation(bugInstance, FieldAnnotation.class);
                if (fieldAnn == null) return false;
                criteriaMatches &= fieldAnn.getFieldName().equals(fieldName);
            }
            if (lineNumber != null) {
                SourceLineAnnotation srcAnn = extractBugAnnotation(bugInstance, SourceLineAnnotation.class);
                if (srcAnn == null) return false;
                criteriaMatches &= srcAnn.getStartLine() <= lineNumber && lineNumber <= srcAnn.getEndLine();
            }
            if (lineNumberApprox != null) {
                SourceLineAnnotation srcAnn = extractBugAnnotation(bugInstance, SourceLineAnnotation.class);
                if (srcAnn == null) return false;
                criteriaMatches &= srcAnn.getStartLine()-1 <= lineNumberApprox && lineNumberApprox <= srcAnn.getEndLine()+1;
            }
            if(jspFile != null) {
                ClassAnnotation classAnn = extractBugAnnotation(bugInstance, ClassAnnotation.class);
                String fullName = classAnn.getClassName().replaceAll("\\.","/").replaceAll("_005f","_").replaceAll("_jsp", ".jsp");
                //String simpleName = fullName.substring(fullName.lastIndexOf("/") + 1);
                criteriaMatches &= fullName.endsWith(jspFile);
            }
            if (multipleChoicesLine != null) {
                SourceLineAnnotation srcAnn = extractBugAnnotation(bugInstance, SourceLineAnnotation.class);
                if (srcAnn == null) return false;
                boolean found = false;
                for(Integer potentialMatch : multipleChoicesLine) {
                    if(srcAnn.getStartLine()-1 <= potentialMatch && potentialMatch <= srcAnn.getEndLine()+1) {
                        found = true;
                    }
                }
                //if(!found) {
                    //log.info("The bug was between lines "+srcAnn.getStartLine()+" and "+srcAnn.getEndLine());
                //}
                criteriaMatches &= found;
            }
            return criteriaMatches;
        } else {
            return false;
        }
    }

    private <T> T extractBugAnnotation(BugInstance bugInstance, Class<T> annotationType) {
        for (BugAnnotation annotation : bugInstance.getAnnotations()) {
            if (annotation.getClass().equals(annotationType)) {
                return (T) annotation;
            }
        }
        return null;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("BugInstance with:\n");
        if (bugType != null) {
            description.appendText("bugType=").appendValue(bugType).appendText(",");
        }
        if (className != null) {
            description.appendText("className=").appendValue(className).appendText(",");
        }
        if (methodName != null) {
            description.appendText("methodName=").appendValue(methodName).appendText(",");
        }
        if (fieldName != null) {
            description.appendText("fieldName=").appendValue(fieldName).appendText(",");
        }
        if (lineNumber != null) {
            description.appendText("lineNumber=").appendValue(lineNumber);
        }
    }
}
