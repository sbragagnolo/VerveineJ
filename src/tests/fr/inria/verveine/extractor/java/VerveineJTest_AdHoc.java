/**
 * Copyright (c) 2010 Simon Denier
 */
package tests.fr.inria.verveine.extractor.java;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import org.junit.Before;
import org.junit.Test;

import test.fr.inria.verveine.core.TestVerveineUtils;
import ch.akuhn.fame.Repository;
import fr.inria.verveine.core.gen.famix.CaughtException;
import fr.inria.verveine.core.gen.famix.DeclaredException;
import fr.inria.verveine.core.gen.famix.Method;
import fr.inria.verveine.core.gen.famix.ThrownException;
import fr.inria.verveine.extractor.java.VerveineJParser;

/**
 * @author Simon Denier
 * @since May 28, 2010
 *
 */
public class VerveineJTest_AdHoc {

	private Repository repo;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		VerveineJParser parser = new VerveineJParser();
		parser.compile(new String[] {"test_src/ad_hoc"});
		parser.renameNamespaces();
		repo = parser.getFamixRepo();
	}

	@Test
	public void testExceptions() {
		// there are two "lire" methods, but both serve our purpose here so we just take the first that will be returned
		Method meth = TestVerveineUtils.detectElement(repo, Method.class, "lire");
		assertNotNull(meth);
		
		fr.inria.verveine.core.gen.famix.Class excepClass = TestVerveineUtils.detectElement(repo, fr.inria.verveine.core.gen.famix.Class.class, "ReadException");
		
		assertEquals(1, meth.getDeclaredExceptions().size());
		DeclaredException exD = meth.getDeclaredExceptions().iterator().next();
		assertSame(meth, exD.getDefiningMethod());
		assertSame(excepClass, exD.getExceptionClass());
		
		assertEquals(1, meth.getThrownExceptions().size());
		ThrownException exT = meth.getThrownExceptions().iterator().next();
		assertSame(meth, exT.getDefiningMethod());
		assertSame(excepClass, exT.getExceptionClass());

		excepClass = TestVerveineUtils.detectElement(repo, fr.inria.verveine.core.gen.famix.Class.class, "IOException");
		
		assertEquals(1,meth.getCaughtExceptions().size());
		CaughtException exC = meth.getCaughtExceptions().iterator().next();
		assertSame(meth, exC.getDefiningMethod());
		assertSame(excepClass, exC.getExceptionClass());
	}

}
