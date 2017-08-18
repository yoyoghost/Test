package test.message;

import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import eh.base.constant.SmsConstant;
import eh.entity.msg.SmsContent;
import eh.task.executor.SmsSendExecutor;
/**
 * 短信发送
 * @author w
 *
 */
public class SmsSendExecutorTester extends TestCase{

	public void testSend(){
		//{"type":"PATIENT","mobile":"13750898256","templateId":"22631","parameter":["马继妹","笕桥社区卫生服务中心 陈涛","浙大附属邵逸夫医院 乳腺甲状腺肿瘤外科 郑和鸣","2015-07-10 全天","4","9：00","①号楼2楼西区","至少提前一个工作日联系，电话0571-86006031、0571-86006667","0571-88890008"]}
		for(int i=0;i<1;i++){
		SmsContent sc=new SmsContent();
		sc.setMobile("15306583327");
		sc.setParameter(new String[]{"翁勇强","采荷社区卫生中心 蔡文娟","浙大附属邵逸夫医院 呼吸内科 陈恩国","2015-07-13 上午","8","9：00","①号楼2楼南区","至少提前一个工作日联系，电话0571-86006031、0571-86006667","0571-88890008"});
		sc.setTemplateId("22631");
		sc.setType(SmsContent.PATIENT);
		//SmsContent sc=new SmsContent(SmsContent.PATIENT,"13735891715",SmsConstant.SECURITY_CODE,new String[]{"22221","5"});
		SmsSendExecutor e=new SmsSendExecutor(sc);
		e.execute();
		
		System.out.println("send");
		try {
			TimeUnit.SECONDS.sleep(2);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		}
	}

}
