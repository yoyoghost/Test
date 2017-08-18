package test.rpc;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by w on 2016/5/13.
 */
public class Server {
    public static void main(String[] args) throws Exception{

        //服务启动
        ClassPathXmlApplicationContext appContext = new ClassPathXmlApplicationContext("classpath:spring.xml");

    }
}
