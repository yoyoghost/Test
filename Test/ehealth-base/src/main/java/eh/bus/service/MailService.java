package eh.bus.service;

import com.sun.mail.util.MailSSLSocketFactory;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class MailService {private static boolean canSend=true;
	public static boolean isCanSend() {
		return canSend;
	}
	public static void setCanSend(boolean canSend) {
		MailService.canSend = canSend;
	}

	public static void sendMail(String title, String content, String to) {
		if(!canSend){
			return;
		}

		try {
			Properties props = new Properties();

			props.setProperty("mail.debug", "true");
			// 发送服务器需要身份验证
			props.setProperty("mail.smtp.auth", "true");
			// 设置邮件服务器主机名
			props.setProperty("mail.host", "smtp.qq.com");
			// 发送邮件协议名称 STP协议发送
			props.setProperty("mail.transport.protocol", "smtp");

			MailSSLSocketFactory sf = new MailSSLSocketFactory();
			sf.setTrustAllHosts(true);
			props.put("mail.smtp.ssl.enable", "true");
			props.put("mail.smtp.ssl.socketFactory", sf);

			Session session = Session.getInstance(props);

			Message msg = new MimeMessage(session);
			// 内容
			msg.setSubject(title);
			StringBuilder builder = new StringBuilder();
			builder.append(content);
			msg.setText(builder.toString());
			msg.setFrom(new InternetAddress("445229845@qq.com"));

			Transport transport = session.getTransport();
			// connect SMTP 邮箱地址 授权码
			transport.connect("smtp.qq.com", "445229845@qq.com", "dtkaqetffabxbhba");

			transport.sendMessage(msg, new Address[] { new InternetAddress(to) });
			transport.close();
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

}
