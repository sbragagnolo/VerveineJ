/**
 * Copyright (c) 2010 Anquetil Nicolas
 */
package fr.inria.verveine.extractor.java;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;

import eu.synectique.verveine.core.gen.famix.Access;
import eu.synectique.verveine.core.gen.famix.Attribute;
import eu.synectique.verveine.core.gen.famix.BehaviouralEntity;
import eu.synectique.verveine.core.gen.famix.ContainerEntity;
import eu.synectique.verveine.core.gen.famix.EnumValue;
import eu.synectique.verveine.core.gen.famix.Invocation;
import eu.synectique.verveine.core.gen.famix.LocalVariable;
import eu.synectique.verveine.core.gen.famix.Method;
import eu.synectique.verveine.core.gen.famix.Namespace;
import eu.synectique.verveine.core.gen.famix.Parameter;
import eu.synectique.verveine.core.gen.famix.ParameterizableClass;
import eu.synectique.verveine.core.gen.famix.ParameterizedType;
import eu.synectique.verveine.core.gen.famix.Reference;
import eu.synectique.verveine.core.gen.famix.Type;

/**
 * @author Nicolas Anquetil
 * @since November 25, 2010
 *
 */
public class VerveineJTest_AdHoc extends VerveineJTest_Basic {

	protected VerveineJParser parser;

	public VerveineJTest_AdHoc() {
	    super(false);
    }

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		new File(VerveineJOptions.OUTPUT_FILE).delete();
		parser = new VerveineJParser();
		repo = parser.getFamixRepo();
	}

	private void parse(String[] sources) {
		parser.configure( sources);
		parser.parse();
		parser.emitMSE(VerveineJOptions.OUTPUT_FILE);
	}

	@Test
	public void testUnresolvedDeclaration() {
		 // note: lire() method unresolved because it throws ReadException which is not parsed here
		parse(new String[]{"test_src/exceptions/ReadClient.java"});

		int nbLire = 0;
		Method lire = null;
		for (Method m : entitiesOfType( Method.class)) {
			if (m.getName().equals("lire")) {
				nbLire++;
				lire = m;
			}
		}
		assertEquals(1, nbLire);
		// actually the extra methods are not in the repository, but they own the invocations
		assertEquals(6, lire.getOutgoingInvocations().size());
	}

	@ Test
	public void testConstructorInvocations() {
		parse(new String[] {"test_src/ad_hoc/DefaultConstructor.java", "test_src/ad_hoc/InvokWithFullPath.java"});

		Method meth = detectFamixElement( Method.class, "methodWithClassScope");
		assertNotNull(meth);

		// test outgoing invocation to constructor
		Collection<Invocation> methOutgoingInvocations = meth.getOutgoingInvocations();
		assertEquals(3, methOutgoingInvocations.size());

		// test invocations' signatures
		for (Invocation invok : methOutgoingInvocations) {
			BehaviouralEntity invoked = firstElt(invok.getCandidates());
			assertTrue( "Unexpected invoked signature: "+invoked.getSignature(),
					invok.getSignature().equals("DefaultConstructor()")
							|| invok.getSignature().equals("JFrame(\"My title\")")
							|| invok.getSignature().equals("methodWithInstanceScope()"));
		}

		// test constructors
		Collection<Method> defaultContructors = entitiesNamed( Method.class, "DefaultConstructor");
		assertEquals(2, defaultContructors.size());
		for (Method m : defaultContructors) {
			int nbParam = m.getParameters().size();
			assertTrue( (nbParam == 0) || (nbParam == 1) );
			assertEquals(1, m.getIncomingInvocations().size());
			assertEquals(1, m.getOutgoingInvocations().size());
		}

		for (Method m : defaultContructors) {
			Invocation invok = firstElt(m.getOutgoingInvocations());
			if (m.getParameters().size() == 0) {
				assertEquals("this(\"For testing\")", invok.getSignature());
			}
			else {
				assertEquals("super(why)", invok.getSignature());
			}
		}

		// get calling method in InvokWithFullPath
		eu.synectique.verveine.core.gen.famix.Class clazz = detectFamixElement( eu.synectique.verveine.core.gen.famix.Class.class, "InvokWithFullPath");
		meth = firstElt(clazz.getMethods());

		// get called method in InvokWithFullPath
		methOutgoingInvocations = meth.getOutgoingInvocations();
		assertEquals(1, methOutgoingInvocations.size());
        Invocation invok = firstElt(methOutgoingInvocations);
		assertEquals("Book(\"The Monster Book of Monsters\",\"Hagrid\")", invok.getSignature());
	}

	@ Test
	public void testStubConstructor() {
		parse(new String[] {"test_src/ad_hoc/DefaultConstructor.java"});

		eu.synectique.verveine.core.gen.famix.Class stubClass = detectFamixElement( eu.synectique.verveine.core.gen.famix.Class.class, "JFrame");
		assertNotNull(stubClass);

		// test outgoing invocation to constructor
		assertEquals(1, stubClass.getMethods().size());

		Method stubConstructor = firstElt(stubClass.getMethods());
	
		assertTrue(stubConstructor.getIsStub());
		assertEquals( "constructor", stubConstructor.getKind());
		
	}

	@Test
	public void testDictionary() {
		parse(new String[] {"test_src/generics/Dictionary.java"});

		ParameterizableClass dico = null;
		for (ParameterizableClass d : entitiesNamed( ParameterizableClass.class, "Dictionary")) {
			if (d.getBelongsTo().getName().equals(AbstractDictionary.DEFAULT_PCKG_NAME)) {
				// note: For testing purposes class Dictionary<B> in ad_hoc is defined without "package" instruction, so it ends up in the default package
				dico = d;
				break;
			}
		}
		assertNotNull(dico);
		assertEquals(7, dico.getMethods().size());
		assertEquals(3, dico.getAttributes().size());

		for (Attribute a : dico.getAttributes()) {
			assertEquals(dico, a.getBelongsTo());
			Type t = a.getDeclaredType();
			assertEquals("Map", t.getName());
			assertEquals(ParameterizedType.class, t.getClass());
		}
	}

	@Test
	public void testStaticMembers() {
		parse(new String[] {"test_src/ad_hoc/DefaultConstructor.java"});

		Method meth = detectFamixElement( Method.class, "methodWithClassScope");
		assertNotNull(meth);
		assertTrue(meth.getHasClassScope());

		meth = detectFamixElement( Method.class, "methodWithInstanceScope");
		assertNotNull(meth);
		assertFalse(meth.getHasClassScope());

		Attribute att = detectFamixElement( Attribute.class, "FIELD_WITH_CLASS_SCOPE");
		assertNotNull(att);
		assertTrue(att.getHasClassScope());

		att = detectFamixElement( Attribute.class, "fieldWithInstanceScope");
		assertNotNull(att);
		assertFalse(att.getHasClassScope());
	}

	@Test
	public void testUnknownMethod() {
		parse(new String[] {"test_src/generics/Dictionary.java"});

		Method meth = detectFamixElement( Method.class, "uniplementedMethod");
		assertNotNull(meth);
		
		assertEquals("uniplementedMethod(?,?)", meth.getSignature());
	}

	@Test
	public void testClassWithNoBindingCreatedAsType() {
		parse(new String[] {"test_src/ad_hoc/Example.java"});

		eu.synectique.verveine.core.gen.famix.Type clazz = detectFamixElement( eu.synectique.verveine.core.gen.famix.Type.class, "BackingStoreException");
		assertNotNull(clazz);
		assertEquals(eu.synectique.verveine.core.gen.famix.Class.class, clazz.getClass());
	}

	@Test
	public void testClassVar() {
		parse(new String[] {"test_src/generics/Dictionary.java"});

		Method meth = detectFamixElement( Method.class, "ensureFamixEntity");
		assertNotNull(meth);

		// might as well do some tests on the method itself
		// not very unit-testing, but it's some more tests
		assertEquals(3, meth.getParameters().size());
		for (Parameter p : meth.getParameters()) {
			if (p.getName().equals("fmxClass")) {
				assertEquals(ParameterizedType.class, p.getDeclaredType().getClass());
				assertEquals("Class", p.getDeclaredType().getName());
			}
			else {
				assertTrue("Unknown parameter for ensureFamixEntity: "+p.getName(),
							p.getName().equals("name") || p.getName().equals("bnd") );
				break;
			}
		}

		// here start the really intended tests
		assertEquals(2, meth.getAccesses().size());  // only 2 non-local variable accessed:  ImplicitVariable.class, Dictionary.mapBind
		boolean classFieldFound = false;
		for (Access acc : meth.getAccesses()) {
			if (acc.getTo().getName().equals("class")) {
				classFieldFound = true;
			}
		}
		assertTrue("ensureFamixEntity does not access <someClass>.class", classFieldFound);
	}

	@Test
	public void testArrayListMatthias() {
		parse(new String[] {"test_src/ad_hoc/Bla.java"});

		assertEquals(15, entitiesOfType( eu.synectique.verveine.core.gen.famix.Class.class).size()); // Bla, Object, String, List, ArrayList, Arrays,Comparable,Serializable,CharSequence, AbstractList, AbstractCollection, Collection, Cloneable, RandomAccess, Iterable
		assertEquals(7,  entitiesOfType( ParameterizableClass.class).size()); //
	}

	@Test
	public void testMethodLocalVariableArgumentTypes() {
		parse(new String[] {"test_src/generics/Dictionary.java"});

		Method meth = detectFamixElement( Method.class, "getEntityByName");
		assertNotNull(meth);
		assertEquals(3, meth.getLocalVariables().size());
		for (LocalVariable var : meth.getLocalVariables()) {
			Type collec;
			if (var.getName().equals("ret")) {
				collec = var.getDeclaredType();
				assertNotNull(collec);
				assertEquals("Collection", collec.getName());
				assertEquals(ParameterizedType.class, collec.getClass());
				assertEquals(1, ((ParameterizedType)collec).getArguments().size());
				Type t = firstElt( ((ParameterizedType)collec).getArguments());
				assertEquals("T", t.getName());
				assertSame(meth, t.getBelongsTo());
			}
			if (var.getName().equals("l_name")) {
				collec = var.getDeclaredType();
				assertNotNull(collec);
				assertEquals("Collection", collec.getName());
				assertEquals(ParameterizedType.class, collec.getClass());
				assertEquals(1, ((ParameterizedType)collec).getArguments().size());
				Type ne = firstElt( ((ParameterizedType)collec).getArguments());
				assertEquals("NamedEntity", ne.getName());
			}
		}
	}

	@Test
	public void testMethodReturnArgumentTypes() {
		parse(new String[] {"test_src/generics/Dictionary.java"});

		Method fmxMethod = detectFamixElement( Method.class, "getEntityByName");
		assertNotNull(fmxMethod);
	}

	@Test
	public void testEnumDecl() {
		parse(new String[] {"test_src/ad_hoc/Card.java", "test_src/ad_hoc/Planet.java"});

		eu.synectique.verveine.core.gen.famix.Class javaLangEnum = detectFamixElement( eu.synectique.verveine.core.gen.famix.Class.class, "Enum");
		assertNotNull(javaLangEnum);
		assertEquals("lang", javaLangEnum.getBelongsTo().getName());
		assertEquals(ParameterizableClass.class, javaLangEnum.getClass());
		
		eu.synectique.verveine.core.gen.famix.Class card = detectFamixElement( eu.synectique.verveine.core.gen.famix.Class.class, "Card");
		assertNotNull(card);

		eu.synectique.verveine.core.gen.famix.Enum rk = detectFamixElement( eu.synectique.verveine.core.gen.famix.Enum.class, "Rank");
		assertNotNull(rk);
		assertEquals("Rank", rk.getName());
		assertEquals(13, rk.getValues().size());
		assertSame(card, rk.getBelongsTo());
		assertNotNull(rk.getSourceAnchor());
		assertEquals(1, rk.getSuperInheritances().size());
		Type rkSuper = firstElt(rk.getSuperInheritances()).getSuperclass();
		assertEquals(ParameterizedType.class, rkSuper.getClass());
		assertEquals(javaLangEnum, ((ParameterizedType)rkSuper).getParameterizableClass());

		EnumValue nine = detectFamixElement( EnumValue.class, "NINE");
		assertNotNull(nine);
		assertEquals("NINE", nine.getName());
		assertSame(rk, nine.getParentEnum());

		eu.synectique.verveine.core.gen.famix.Enum st = detectFamixElement( eu.synectique.verveine.core.gen.famix.Enum.class, "Suit");
		assertNotNull(st);
		assertEquals("Suit", st.getName());
		assertEquals(1, st.getSuperInheritances().size());
		Type stSuper = firstElt(st.getSuperInheritances()).getSuperclass();
		assertEquals(ParameterizedType.class, stSuper.getClass());
		assertEquals(javaLangEnum, ((ParameterizedType)stSuper).getParameterizableClass());
		assertEquals(4, st.getValues().size());
		assertSame(detectFamixElement( Namespace.class, "ad_hoc"), st.getBelongsTo());

		EnumValue hrt = detectFamixElement( EnumValue.class, "HEARTS");
		assertNotNull(hrt);
		assertEquals("HEARTS", hrt.getName());
		assertSame(st, hrt.getParentEnum());

		assertEquals(3, card.getAttributes().size());
		for (Attribute a : card.getAttributes()) {
			if (a.getName().equals("rank")) {
				assertEquals(rk, a.getDeclaredType());
			}
			else if (a.getName().equals("suit")) {
				assertEquals(st, a.getDeclaredType());
			}
			else {
				assertEquals("protoDeck", a.getName());
			}
		}

		eu.synectique.verveine.core.gen.famix.Enum pl = detectFamixElement( eu.synectique.verveine.core.gen.famix.Enum.class, "Planet");
		assertNotNull(pl);
		assertEquals("Planet", pl.getName());
		assertEquals(1, pl.getSuperInheritances().size());
		Type plSuper = firstElt(pl.getSuperInheritances()).getSuperclass();
		assertEquals(ParameterizedType.class, plSuper.getClass());
		assertEquals(javaLangEnum, ((ParameterizedType)plSuper).getParameterizableClass());
		assertSame(detectFamixElement( Namespace.class, "ad_hoc"), pl.getBelongsTo());
		assertEquals(8, pl.getValues().size());
		assertEquals(4, pl.getAttributes().size());
		assertEquals(7+2, pl.getMethods().size()); // 7 methods + <initializer> + implicit used: values()
	}

	@Test
	public void testEnumAccess() {
		parse(new String[] {"test_src/ad_hoc/Card.java", "test_src/ad_hoc/Planet.java"});

		eu.synectique.verveine.core.gen.famix.Enum st = detectFamixElement( eu.synectique.verveine.core.gen.famix.Enum.class, "Suit");
		assertNotNull(st);
		assertEquals(4, st.getValues().size());
		boolean foundClubs = false;
		for (EnumValue v : st.getValues()) {
			if (v.getName().equals("CLUBS")) {
				foundClubs = true;
				assertEquals(1, v.numberOfIncomingAccesses());
				Access access = firstElt(v.getIncomingAccesses());
				assertEquals("toString", access.getFrom().getName());
			}
		}
		assertTrue("Did not find CUBS EnumValue in Suit Enum", foundClubs);
		
		eu.synectique.verveine.core.gen.famix.Enum pl = detectFamixElement( eu.synectique.verveine.core.gen.famix.Enum.class, "Planet");
		assertNotNull(pl);

		assertEquals(8, pl.getValues().size());
		for (EnumValue v : pl.getValues()) {
			if (v.getName().equals("EARTH")) {
				assertEquals(1, v.getIncomingAccesses().size());
			}
			else {
				assertEquals(0, v.getIncomingAccesses().size());
			}
		}

		assertEquals(4, pl.getAttributes().size());
		for (Attribute a : pl.getAttributes()) {
			if ( a.getName().equals("G") || a.getName().equals("radius") || a.getName().equals("i") ) {
				assertEquals(2, a.getIncomingAccesses().size());
			}
			else if (a.getName().equals("mass")) {
				assertEquals(4, a.getIncomingAccesses().size());
			}
			else {
				fail("Unknown attribute of Enum Planet: "+a.getName());
			}
		}

		assertEquals(7+2, pl.getMethods().size());  // see testEnumDecl()
		for (Method m : pl.getMethods()) {
			if ( m.getName().equals("Planet") || m.getName().equals("main") || m.getName().equals("sillyArrayAssignement")
					|| m.getName().equals(JavaDictionary.INIT_BLOCK_NAME) ) {
				assertEquals(0, m.getIncomingInvocations().size());
			}
			else if ( m.getName().equals("mass") || m.getName().equals("surfaceWeight") || m.getName().equals("toString") ) {
				assertEquals(1, m.getIncomingInvocations().size());
			}
			else if ( m.getName().equals("surfaceGravity")  || m.getName().equals("values") ) {
				assertEquals(2, m.getIncomingInvocations().size());
			}
			else if (m.getName().equals("radius")) {
				assertEquals(3, m.getIncomingInvocations().size());
			}
			else {
				fail("Unknown method of Enum Planet: "+m.getName());
			}
		}
	}

	@Test
	public void testReadWriteAccess() {
		parse(new String[] {"test_src/ad_hoc/Planet.java"});

		Attribute i_att = null;
		Attribute mass_att = null;
		Access access = null;

		eu.synectique.verveine.core.gen.famix.Enum pl = detectFamixElement( eu.synectique.verveine.core.gen.famix.Enum.class, "Planet");
		assertNotNull(pl);

		assertEquals(4, pl.getAttributes().size());
		for (Attribute a : pl.getAttributes()) {
			if ( a.getName().equals("i") ) {
				i_att = a;
			}
			else if (a.getName().equals("mass")) {
				mass_att = a;
			}
		}
		
		assertNotNull("Attribute i in Planet not found", i_att);
		assertEquals(2, i_att.getIncomingAccesses().size());
		access = firstElt(i_att.getIncomingAccesses());
		if (access.getAccessor().getName().equals("sillyArrayAssignement")) {
            assertFalse(access.getIsWrite());
        }
        else {
		    assertTrue(access.getIsWrite());
        }
		
		assertNotNull("Attribute mass in Planet not found", mass_att);
		assertEquals(4, mass_att.getIncomingAccesses().size());
		for (Access acc : mass_att.getIncomingAccesses() ) {
			if ( acc.getAccessor().getName().equals("Planet") ||
				 acc.getAccessor().getName().equals("sillyArrayAssignement") ) {
				assertTrue(acc.getIsWrite());
			}
			else {
				assertFalse("Access to mass is write in method: " + acc.getAccessor().getSignature(), acc.getIsWrite());
			}
		}
	}

	@Test
	public void testStaticInitializationBlock() {
		parse(new String[] {"test_src/ad_hoc/Card.java", "test_src/ad_hoc/Planet.java", "test_src/ad_hoc/InvokWithFullPath.java", "test_src/ad_hoc/DefaultConstructor.java"});

		Collection<Method> l_meth = entitiesNamed( Method.class, JavaDictionary.INIT_BLOCK_NAME);
		assertEquals(3, l_meth.size());
		for (Method meth : l_meth) {
			assertEquals(JavaDictionary.INIT_BLOCK_NAME+"()", meth.getSignature());
			if (meth.getParentType().getName().equals("Card")) {
				assertEquals(5, meth.getOutgoingInvocations().size());
			}
			else if (meth.getParentType().getName().equals("Planet")) {
				assertEquals(0, meth.getOutgoingInvocations().size());
			}
			else if (meth.getParentType().getName().equals("DefaultConstructor")) {
				assertEquals(0, meth.getOutgoingInvocations().size());
			}
			else {
				fail("Unknown class with an <Initializer> method: " + meth.getParentType().getName());
			}
		}
	}

	@Test
	public void testStaticInitializationBlockNewString() {
		parse(new String[] {"test_src/ad_hoc/EnumConstWithInitNewString.java"});

		eu.synectique.verveine.core.gen.famix.Enum fmx = detectFamixElement( eu.synectique.verveine.core.gen.famix.Enum.class, "EnumConstWithInitNewString");
		assertNotNull(fmx);

		assertEquals(1, fmx.numberOfValues());
		assertEquals("ONE", firstElt(fmx.getValues()).getName());
		
		assertEquals(2, fmx.getMethods().size());  // constructor + INIT_BLOCK
	}

	@Test
	public void testWrongMethodOwner() {
		parse(new String[] {"test_src/ad_hoc/InvokerWrongOwner.java"});

		Method meth = detectFamixElement( Method.class, "methodWrongOwner");
		assertNotNull(meth);

		assertEquals(detectFamixElement( eu.synectique.verveine.core.gen.famix.Class.class, "SuperWrongOwner"), meth.getParentType());
	}
	
	@Test
	public void testModifiers() {
		parse(new String[] {"test_src/ad_hoc/Modifiers.java"});

		Attribute attribute = detectFamixElement( Attribute.class, "privateFinalAttribute");
		assertNotNull(attribute);

		assertEquals(2, attribute.getModifiers().size());
		assertTrue(attribute.getModifiers().contains(JavaDictionary.MODIFIER_PRIVATE));
		assertTrue(attribute.getModifiers().contains(JavaDictionary.MODIFIER_FINAL));
	}

	@Test
	public void testMultipleSignatures() {
		parse(new String[] {"test_src/ad_hoc/MultipleSignatures.java"});

		eu.synectique.verveine.core.gen.famix.Class throwable = detectFamixElement( eu.synectique.verveine.core.gen.famix.Class.class, "Throwable");
		assertNotNull(throwable);
		assertEquals(2, throwable.getMethods().size()); // printStackTrace() & printStackTrace(PrintWriter)

		Method regular =  detectFamixElement( Method.class, "callToRegularPrintStackTrace");
		assertNotNull(regular);
		assertEquals(1, regular.getOutgoingInvocations().size());
		
		Method withParam =  detectFamixElement( Method.class, "callToPrintStackTraceWithParam");
		assertNotNull(withParam);
		assertEquals(3, withParam.getOutgoingInvocations().size());  // printStackTrace(new PrintWriter(new StringWriter()))
	}

	@Test
	public void testInvokSelfNoBinding(){
		// TODO sould use source within ad_hoc
		parse(new String[] {"test_src/annotations/Serializer.java"});

		Method seri = detectFamixElement( Method.class, "serialize");
		assertNotNull(seri);
		ContainerEntity owner = seri.getBelongsTo(); 
		assertEquals("Serializer", owner.getName());  // just checking
		
		for (Invocation invok : seri.getOutgoingInvocations()) {
			BehaviouralEntity invoked = firstElt(invok.getCandidates());
			if (invoked.getName().equals("serializeProperty")) {
				assertEquals(owner, invoked.getBelongsTo());
			}
		}
	}

	@Test
	public void testInstanceOf() {
		parse(new String[] {"test_src/ad_hoc/Planet.java"});

		Method m = detectFamixElement( Method.class, "sillyArrayAssignement");
		assertNotNull(m);

		Collection<Reference> refs = m.getOutgoingReferences();
		assertEquals(2, refs.size());

		Type referred;
		Iterator<Reference> iter = refs.iterator();

		referred = iter.next().getTarget();
		if (referred.getName().equals("IOException")) {
		    referred = iter.next().getTarget();
		    assertEquals("Planet", referred.getName());
        }
        else {
		    assertEquals("Planet", referred.getName());
		    referred = iter.next().getTarget();
		    assertEquals("IOException", referred.getName());

        }
	}

	@Test
	public void testPublicStaticInnerClass() {
		parse(new String[] {"test_src/ad_hoc/StaticInnerClass.java"});

		eu.synectique.verveine.core.gen.famix.Class clazz = detectFamixElement( eu.synectique.verveine.core.gen.famix.Class.class, "ThisIsTheStaticInnerClass");
		assertNotNull(clazz);

		// assertTrue(clazz.getIsPublic()); --- set as a modifier 
		assertEquals(2, clazz.getModifiers().size());
		for (String mod : clazz.getModifiers()) {
			assertTrue( mod.equals(JavaDictionary.MODIFIER_PUBLIC) || mod.equals(JavaDictionary.MODIFIER_STATIC) );
		}
	}

    @Test
    public void testSuperConstructorInvocation() {
 		parse(new String[] {"test_src/ad_hoc/StubSuperConstructor.java"});

       Collection<Method> meths = entitiesNamed( Method.class, "StubSuperConstructor");

        assertEquals(2, meths.size());
        for (Method meth : meths) {
        	if (meth.numberOfParameters() == 0) {
        		// empty constructor
				assertEquals(0, meth.getOutgoingInvocations().size());
			}
        	else {
				// the other (not empty) constructor
				assertEquals(1, meth.getOutgoingInvocations().size());
				Invocation invok = firstElt(meth.getOutgoingInvocations());
				BehaviouralEntity invoked = firstElt(invok.getCandidates());
				assertNotNull(invoked);
				assertEquals("ArrayList<String>", invoked.getName() );
			}
		}
    }

    @Test
	public void testMethodModifiers(){
		parse(new String[] {"test_src/ad_hoc/Modifiers.java"});

		Collection<Method> meths = entitiesNamed( Method.class, "methodModifiers");

		assertEquals(1, meths.size());
		Method method = firstElt(meths);

		assertNotNull(method);
		assertEquals(6, method.getModifiers().size());
		assertTrue( method.getModifiers().contains("transient"));
		assertTrue( method.getModifiers().contains("public"));
		assertTrue( method.getModifiers().contains("static"));
		assertTrue( method.getModifiers().contains("final"));
		assertTrue( method.getModifiers().contains("volatile"));
		assertTrue( method.getModifiers().contains("synchronized"));
	}

	@Test
	public void testAttributeModifiers(){
		parse(new String[] {"test_src/ad_hoc/Modifiers.java"});

		Attribute attribute = firstElt(entitiesNamed( Attribute.class, "attribute"));

		assertNotNull(attribute);
		assertEquals(5, attribute.getModifiers().size());
		assertTrue( attribute.getModifiers().contains("public"));
		assertTrue( attribute.getModifiers().contains("static"));
		assertTrue( attribute.getModifiers().contains("transient"));
		assertTrue( attribute.getModifiers().contains("volatile"));
		assertTrue( attribute.getModifiers().contains("final"));
	}

}