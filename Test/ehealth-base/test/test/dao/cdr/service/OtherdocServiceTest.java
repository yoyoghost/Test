package test.dao.cdr.service;

import eh.entity.cdr.DocIndex;
import eh.entity.cdr.Otherdoc;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zhangx on 2016/5/24.
 */
public class OtherdocServiceTest extends TestCase {

    private static ClassPathXmlApplicationContext appContext;

    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

    public void testuploadDocIndexs(){
        List<Otherdoc> indexs =new ArrayList<Otherdoc>();
        Otherdoc index=new Otherdoc();
        index.setMpiid("4028811454613685015461376f940000");
        index.setDocName("1.png");
        index.setDocFormat("13");
        index.setDocType("1");
        index.setDocContent(1);
        indexs.add(index);

        Otherdoc index2=new Otherdoc();
        index2.setMpiid("4028811454613685015461376f940000");
        index2.setDocName("2.png");
        index2.setDocFormat("13");
        index2.setDocContent(2);
        indexs.add(index2);

        Otherdoc index3=new Otherdoc();
        index3.setMpiid("4028811454613685015461376f940000");
        index3.setDocName("3.png");
        index3.setDocFormat("13");
        index3.setDocType("3");
        indexs.add(index3);

        Otherdoc index4=new Otherdoc();
        index4.setDocName("4.png");
        index4.setDocFormat("13");
        index4.setDocType("4");
        index4.setDocContent(4);
        indexs.add(index4);

        Otherdoc index5=new Otherdoc();
        index5.setMpiid("4028811454613685015461376f940000");
        index5.setDocName("5.png");
        index5.setDocFormat("13");
        index5.setDocType("5");
        index5.setDocContent(5);
        indexs.add(index5);

        //需要js测试
        //[{"mpiid":"4028811454613685015461376f940000","docType":"1","docName":"1.png","docFormat":"13","docContent":1,"docTypeText":"检验报告","docFormatText":"JPG"},{"mpiid":"4028811454613685015461376f940000","docName":"2.png","docFormat":"13","docContent":2,"docFormatText":"JPG"},{"mpiid":"4028811454613685015461376f940000","docType":"3","docName":"3.png","docFormat":"13","docTypeText":"处方","docFormatText":"JPG"},{"docType":"4","docName":"4.png","docFormat":"13","docContent":4,"docTypeText":"治疗记录","docFormatText":"JPG"},{"mpiid":"4028811454613685015461376f940000","docType":"5","docName":"5.png","docFormat":"13","docContent":5,"docTypeText":"住院病历","docFormatText":"JPG"}]
        System.out.println(ctd.util.JSONUtils.toString(indexs));
    }
}
