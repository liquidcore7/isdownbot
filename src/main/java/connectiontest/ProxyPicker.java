package connectiontest;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

public class ProxyPicker extends ProxySelector {

    private List<Proxy> userProxyList;

    public ProxyPicker(List<Proxy> initList) {
        userProxyList = initList;
    }

    public void setUserProxyList(List<Proxy> userProxyList) {
        this.userProxyList = userProxyList;
        this.userProxyList.add(0, Proxy.NO_PROXY);
    }

    @Override
    public List<Proxy> select(URI uri) {
        return userProxyList;
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
        userProxyList.remove(new Proxy(Proxy.Type.HTTP, socketAddress));
    }
}
