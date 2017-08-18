package eh.util;

import ctd.resource.ResourceCenter;
import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateNotFoundException;
import org.apache.log4j.Logger;
import org.springframework.core.io.Resource;

import java.io.*;

public class FreemarkerUtil {

	public static final Logger log = Logger.getLogger(FreemarkerUtil.class);

	public static Template getTemplate(String tempName){
		Configuration  cfg=new Configuration(Configuration.VERSION_2_3_23);
		Template temp=null;
		try {
			
			Resource r = ResourceCenter.load("classpath:","eh/cdr/htmlmodel/model/");
			cfg.setDirectoryForTemplateLoading(r.getFile());
			cfg.setDefaultEncoding("UTF-8");
			temp=cfg.getTemplate(tempName);
		} catch (TemplateNotFoundException e) {
			log.error(e);
		} catch (MalformedTemplateNameException e) {
			log.error(e);
		} catch (ParseException e) {
			log.error(e);
		} catch (IOException e) {
			log.error(e);
		}
		return temp;
	}
	public static Writer getWriter(String htmlName){
		Writer writer=null;
		try {
			Resource r = ResourceCenter.load("classpath:","eh/cdr/htmlmodel/outhtml/");
			File dirct = r.getFile();
			String path = dirct.getPath();
			log.info("getWriter.path+htmlName="+path+htmlName);
			 writer  = new OutputStreamWriter(new FileOutputStream(path+"/"+htmlName),"UTF-8");
		} catch (Exception e) {
			log.error(e);
		} 
		return writer;
	}
}
