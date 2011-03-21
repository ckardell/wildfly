/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.completion.address.test;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.jboss.as.cli.completion.mock.MockNode;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class NodeNameFormattingTestCase extends AbstractAddressCompleterTest {

    public NodeNameFormattingTestCase() {
        super();

        MockNode root = addRoot("root");
        root.addChild("colon:");
        root.addChild("slash/");
        root.addChild("both/:");
        root.addChild("quotes:a/b\"c\"d");
    }

    @Test
    public void testAll() {
        assertEquals(Arrays.asList("both/:", "colon:", "quotes:a/b\"c\"d", "slash/"), fetchCandidates("/root="));
    }

    @Test
    public void testColon() {
        assertEquals(Arrays.asList("\"colon:\""), fetchCandidates("/root=c"));
    }

    @Test
    public void testSlash() {
        assertEquals(Arrays.asList("\"slash/\""), fetchCandidates("/root=s"));
    }

    @Test
    public void testBoth() {
        assertEquals(Arrays.asList("\"both/:\""), fetchCandidates("/root=b"));
    }

    @Test
    public void testQuotes() {
        assertEquals(Arrays.asList("\"quotes:a/b\\\"c\\\"d\""), fetchCandidates("/root=q"));
    }
}
