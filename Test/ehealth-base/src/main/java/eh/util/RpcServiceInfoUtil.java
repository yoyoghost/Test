package eh.util;


import com.google.common.collect.ImmutableList;
import ctd.net.rpc.desc.support.MethodDesc;
import ctd.net.rpc.desc.support.ProviderUrl;
import ctd.net.rpc.desc.support.ServiceDesc;
import ctd.net.rpc.exception.RpcException;
import ctd.net.rpc.registry.ServiceRegistry;
import ctd.net.rpc.registry.exception.RegistryException;
import ctd.net.rpc.transport.exception.TransportException;
import ctd.net.rpc.util.ServiceAdapter;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.AppDomainRpcUrlDAO;
import eh.entity.base.AppDomainRpcUrl;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Created by zxq on 2016/7/19.
 */
public class RpcServiceInfoUtil {
    private static final Logger logger = LoggerFactory.getLogger(RpcServiceInfoUtil.class);
    public static InvokeLimiter invokeLimiter;
    private static final int limitCode = 701;

    private static boolean isLimitReady(){
        if (invokeLimiter == null && AppContextHolder.containBean("invokeLimiter")){
            invokeLimiter = AppContextHolder.getBean("invokeLimiter", InvokeLimiter.class);
        }
        if (invokeLimiter == null){
            return false;
        }
        return true;
    }


    public static  Object  getClientService(Class c,String serverName,String methodName,Object... param){
        try {
            //RpcServiceInfoStore rpcServiceInfoStore = AppDomainContext.getBean("eh.rpcServiceInfoStore", RpcServiceInfoStore.class);
            //RpcServiceInfo info = rpcServiceInfoStore.getInfo(serverName);
        	//registerHisService(info.getUrl(), c, serverName);
        	AppDomainRpcUrlDAO dao = DAOFactory.getDAO(AppDomainRpcUrlDAO.class);
        	AppDomainRpcUrl rpcUrl = null;
        	//logger.info("his服务{}没有找到方法{}",serverName,methodName);
        	if(serverName.indexOf(".") != -1){        		
        		rpcUrl = dao.getByAppDomainIdFromCache(serverName.substring(0, serverName.indexOf(".")));
        	}else{
        		rpcUrl = dao.getByAppDomainIdFromCache(serverName);        		
        	} 
//        	rpcUrl.setRpcUrl("tcp://192.168.9.44:8090?codec=hessian");
        	if(rpcUrl == null){
        		return null;
        	}        	
        	registerHisService(rpcUrl.getRpcUrl(), c, serverName);
            return hisRpcInvoke(serverName, methodName, param);
        }catch (NullPointerException e){
            logger.error("his服务{}没有找到方法{}",serverName,methodName);
        }catch (DAOException e){
            e.printStackTrace();
            logger.error(e.getMessage());
        }catch (RegistryException e){
            if (StringUtils.isNoneEmpty(e.getMessage()) && e.getMessage().endsWith("not found.")){
                logger.error("his服务{}不存在",serverName,methodName);
            }
            throw e;
        }catch (Exception e){
            logger.error("调用his RPC服务出错:"+serverName + "." + methodName +"|"+e.getMessage(), e);
            if (e instanceof RpcException && limitCode == ((RpcException) e).getCode()){
                throw (RpcException) e;
            }
        }
        return null;
    }

    private static Object hisRpcInvoke(String beanName, String method, Object... params) throws Exception {
        try {
            if (isLimitReady()){
                if (!invokeLimiter.plus(beanName)){
                    throw new RpcException(limitCode, StringUtils.join("EXCEED THE LIMIT:",beanName,"@",method));
                }
            }
            if (params == null){
                return ServiceAdapter.invoke(beanName, method);
            }else
                return ServiceAdapter.invoke(beanName, method, params);
        }finally {
            if (isLimitReady()){
                invokeLimiter.minus(beanName);
            }
        }

    }

    private static void registerHisService(String url, Class service, String beanName){
        boolean isRegister = false;
        ServiceRegistry registry = AppDomainContext.getRegistry();
        ServiceDesc serviceDesc = null;
        try {
            serviceDesc = registry.find(beanName);
            if (serviceDesc != null){
                List<ProviderUrl> urls = serviceDesc.providerUrls();
                for (ProviderUrl u: urls){
                    if (u.getUrl().equals(url)){
                        isRegister = true;
                        break;
                    }
                }
                if (!isRegister){
                    serviceDesc.updateProviderUrls(ImmutableList.of(new ProviderUrl(url, true)));
                    isRegister = true;
                }
            }
        }catch (RegistryException e){
        }
        if (!isRegister){
            logger.info("service ["+beanName+"] is not registered, start registering by url:" + url);
            serviceDesc = new ServiceDesc();
            serviceDesc.setId(beanName);
            Method[] ms = service.getMethods();
            List<MethodDesc> descs = new ArrayList<>();
            for(Method m : ms){
                if(m.isAnnotationPresent(RpcService.class)){
                    RpcService an = m.getAnnotation(RpcService.class);
                    MethodDesc mc = new MethodDesc(service,m);
                    mc.setInboundCompression(an.inboundCompression());
                    mc.setOutboundCompression(an.outboundCompression());
                    descs.add(mc);
                }
            }
            serviceDesc.setMethods(descs);
            serviceDesc.addProviderUrl(new ProviderUrl(url, true));
            registry.add(serviceDesc);
        }

    }

    static class RpcRetryInvoker implements Callable{
        private int retry = 3;
        private int time = 0;
        private String beanName;
        private String method;
        private Object[] params;

        public RpcRetryInvoker(String beanName, String method, Object... params) {
            this.beanName = beanName;
            this.method = method;
            this.params = params;
        }

        public void setRetry(int retry) {
            this.retry = retry;
        }

        @Override
        public Object call() throws Exception {
            while (time < retry){
                try {
                    if (params == null){
                        return ServiceAdapter.invoke(beanName, method);
                    }else
                        return ServiceAdapter.invoke(beanName, method, params);
                }catch (TransportException e){
                    Throwable t = e.getCause();
                    if (e.isConnectFailed()){
                        time++;
                        TimeUnit.SECONDS.sleep(1l);
                        continue;
                    }else {
                        throw e;
                    }
                }catch (Exception e){
                    throw e;
                }
            }
            throw new RpcException(StringUtils.join("invoke service [",beanName,".",method,"] failed"));
        }
    }

}
