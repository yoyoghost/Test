package eh.util;

import ctd.util.annotation.RpcService;
import ctd.util.context.ContextUtils;

import java.util.Date;

public class ServerDateService{
	

	@RpcService
	public  Date getServerDate(){
		return (Date) ContextUtils.get("server.date.today");
	}
	
}
