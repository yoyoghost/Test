package test.dao.cdr;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.HashMap;

import junit.framework.TestCase;

import org.springframework.core.io.Resource;

import ctd.resource.ResourceCenter;
import eh.util.FreemarkerUtil;
import freemarker.template.Template;
import freemarker.template.TemplateException;

public class FreemarkerTester extends TestCase{

	
	public void testHelloFm(){
		Template tp=FreemarkerUtil.getTemplate("report.ftl");
		Writer wt=FreemarkerUtil.getWriter("report.html");
		HashMap<String, String> map=new HashMap<String, String>();
		map.put("reportName", "血常规");
		map.put("patientName", "王宁武");
		
		try {
			tp.process(map, wt);
			System.out.println("process success");
		} catch (TemplateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public void testDelete(){
		try {
			File f=new File("src/eh/cdr/htmlmodel/outhtml/report.html");
			Resource r = ResourceCenter.load("classpath:","eh/cdr/htmlmodel/outhtml/"+"report.html");
			BufferedReader br = new BufferedReader(new InputStreamReader(r.getInputStream()));
			StringBuffer sb=new StringBuffer();
			
			while (br.readLine()!=null) {
			   sb.append(br.readLine());	
				
			}
			System.out.println(sb.toString());
			File htmlFile = r.getFile();
			if(r.exists()&&htmlFile.isFile()){
				boolean issucc=htmlFile.getAbsoluteFile().delete();
				System.out.println(issucc);
				
				
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
