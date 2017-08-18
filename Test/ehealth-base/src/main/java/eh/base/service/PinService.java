package eh.base.service;

import ctd.util.PyConverter;
import ctd.util.annotation.RpcService;

public class PinService {

	/**
	 * 生成拼音码服务
	 * @author hyj
	 * @param words
	 * @return
	 */
	@RpcService
	public String convertPin(String words){
		return PyConverter.getFirstLetter(words);
	}
	
}
