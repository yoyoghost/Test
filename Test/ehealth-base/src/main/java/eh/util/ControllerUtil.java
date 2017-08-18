package eh.util;

import ctd.access.AccessTokenController;
import ctd.account.user.UserController;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.mvc.weixin.service.WXAppLoader;
import ctd.util.AppContextHolder;
import eh.utils.params.ParamUtils;

public class ControllerUtil {
	
	/**
	 * 刷新特定用户缓存
	 * @author ZX
	 * @date 2015-8-12  下午9:29:34
	 * @param uid
	 */
	public static void reloadUserByUid(String uid) throws ControllerException {
		UserController.instance().getUpdater().reload(uid);
	}
	
	/**
	 * 刷新全部缓存
	 * @author ZX
	 * @date 2015-8-12  下午9:29:34
	 * @param uid
	 */
	public static void reloadAllUser() {
		UserController.instance().reloadAll();
	}
	
	/**
	 * 刷新字典缓存
	 * @author ZX
	 * @date 2015-8-12  下午9:29:34
	 * @param uid
	 */
	public static void reloadAllDictionary() {
		DictionaryController.instance().reloadAll();
	}
	
	/**
	 * 刷新指定字典
	 * @author ZX
	 * @date 2015-8-17  下午6:00:49
	 * @param id='eh.base.dictionary.Organ'
	 */
	public static void reloadDictionaryById(String id) throws ControllerException {
		DictionaryController.instance().getUpdater().reload(id);
	}

	/**
	 * 删除指定用户token值
     */
	public static void removeAccessTokenById(String token) throws ControllerException {
		AccessTokenController.instance().getUpdater().remove(token);
	}

	/**
	 * 刷新指定用户token值
	 */
	public static void reloadAccessTokenById(String token) throws ControllerException {
		AccessTokenController.instance().reload(token);
	}

	/**
	 * 刷新指定wxapp
	 */
	public static void reloadWXAppByAppId(String appId) throws ControllerException {
		WXAppLoader manager = AppContextHolder.getBean("eh.wxAppLoader", WXAppLoader.class);
		manager.reload(appId);
	}

	/**
	 * 刷新指定常量值
	 * @param paramName
     */
	public static void reloadParamByName(String paramName){
		ParamUtils.reload(paramName);
	}
}
