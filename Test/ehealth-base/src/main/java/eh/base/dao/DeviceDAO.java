package eh.base.dao;

import ctd.account.AccountCenter;
import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.controller.exception.ControllerException;
import ctd.mvc.controller.util.UserRoleTokenUtils;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.security.exception.SecurityException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.entity.base.Device;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.List;

public abstract class DeviceDAO extends HibernateSupportDelegateDAO<Device>
		implements DBDictionaryItemLoader<Device> {
	public static final Logger log = Logger.getLogger(DeviceDAO.class);

	public DeviceDAO() {
		super();
		this.setEntityName(Device.class.getName());
		this.setKeyField("id");
	}

//	/**
//	 * 根据userid查询Device
//	 * app3.2版本 新增角色信息，不能使用getByUserId，此方法作废
//	 * @param id
//	 * @return
//	 */
//	@DAOMethod
//	public abstract Device getByUserId(String userId);

	@DAOMethod(sql="from Device where userId=:userId and roleId=:roleId and (os='IOS' or os='Android' ) order by lastModify desc")
	public abstract List<Device> findLastLoginApps(@DAOParam("userId")String userId, @DAOParam("roleId")String roleId);

	@DAOMethod(sql = "from Device where urt=:urt and (os='IOS' or os='Android' ) order by lastModify desc")
	public abstract List<Device> findIosAndAndroidByUrt(@DAOParam("urt") Integer urt);

	@DAOMethod(sql = "from Device where userId=:userId and roleId=:roleId and os='WX' order by lastModify desc")
	public abstract List<Device> findWXByUserIdAndRoleId(@DAOParam("userId")String userId, @DAOParam("roleId")String roleId);

	@DAOMethod(sql = "from Device where userId=:userId and roleId=:roleId and os='WX' and token=:token order by lastModify desc")
	public abstract List<Device> findWXByUserIdAndRoleIdAndToken(@DAOParam("userId")String userId, @DAOParam("roleId")String roleId,@DAOParam("token")String token);

	@DAOMethod(sql = "select id from Device where userId=:userId and roleId=:roleId and os='WX' and token like :token order by lastModify desc")
	public abstract List<Integer> findWXByUserIdAndRoleIdAndTokenExt(@DAOParam("userId")String userId, @DAOParam("roleId")String roleId,@DAOParam("token")String token);


	@DAOMethod(sql = "from Device where urt=:urt and os='WX' order by lastModify desc")
	public abstract List<Device> findWXByUrt(@DAOParam("urt") Integer urt);

	/**
	 * 用于sms优化临时方案
     * @return
     */
	@RpcService
	public Device getLastWxLoginDeviceByToken(String userId, String roleId, String token){
		List<Device> list= findWXByUserIdAndRoleIdAndToken(userId, roleId,token);
		return list.isEmpty()?null:list.get(0);
	}

	@RpcService
	public Device getLastWxLoginDevice(String userId, String roleId){
		List<Device> list= findWXByUserIdAndRoleId(userId, roleId);
		return list.isEmpty()?null:list.get(0);
	}

	@RpcService
	public Device getLastWxLoginDevice(Integer urt){
		List<Device> list= findWXByUrt(urt);
		return list.isEmpty()?null:list.get(0);
	}

	/**
	 * 查询用户最新登录的有效设备列表
	 * @param userId
	 * @param urt
	 * @param os
     * @return
     */
	@RpcService
	@DAOMethod(sql = "FROM Device WHERE userId = :userId AND urt = :urt AND os= :os AND status=1 ORDER BY lastModify desc ")
	public abstract List<Device> findAvailableUserDeviceListOrderByLastModifyDesc(@DAOParam("userId") String userId,
														 @DAOParam("urt") Integer urt,
														 @DAOParam("os") String os);

	/**
	 * 查询用户最新登录的设备列表
	 * @param userId
	 * @param urt
	 * @param os
	 * @return
	 */
	@DAOMethod(sql = "FROM Device WHERE userId = :userId AND urt = :urt AND os= :os  ORDER BY lastModify desc ")
	public abstract List<Device> findUserDeviceListOrderByLastModifyDesc(@DAOParam("userId") String userId,
																					@DAOParam("urt") Integer urt,
																					@DAOParam("os") String os);


	@DAOMethod(sql="from Device where userId=:userId and roleId='doctor' and os=:os")
	public abstract Device getDocAppByUserIdAndOs(@DAOParam("userId")String userId, @DAOParam("os")String os);

	@RpcService
	public Device getLastLoginAPP(String userId,String roleId){
		List<Device> list=findLastLoginApps(userId,roleId);
		if(list.size()==0){
			return null;
		}else{
			return list.get(0);
		}
	}

	@RpcService
	public Device getLastLoginAPP(Integer urt){
		List<Device> list=findIosAndAndroidByUrt(urt);
		if(list.size()==0){
			return null;
		}else{
			return list.get(0);
		}
	}

	@DAOMethod(orderBy = "lastModify desc")
	public abstract List<Device> findByUrt(int urt);

	@RpcService
	public Device getLast(int urt){
		List<Device> ls = findByUrt(urt);
		return ls.isEmpty()?null:ls.get(0);
	}

	@RpcService
	public Device getLast(String userId,String roleId){
		try {
			User user = AccountCenter.getUser(userId);
			List<UserRoleToken> rs = user.findUserRoleTokenByRoleId(roleId);
			if(!rs.isEmpty()){
				return getLast(rs.get(0).getId());
			}
		} catch (ControllerException e) {
			log.error("getLast() error:"+e.getMessage());
		}
		return null;
	}


	/**
	 * 保存或更新device(医生APP端使用)
	 * @param device
	 */
	@RpcService
	public void addDevice(Device device) {
		saveOrUpdate(device);
	}

	private void saveOrUpdate(Device device) {
		isValidDataForSaveOrUpdate(device);

		String userId=device.getUserId();
		UserRoleToken userRoleToken = UserRoleToken.getCurrent();


		Date d = new Date();
		device.setLastModify(d);
		device.setRoleId(userRoleToken.getRoleId());
		device.setUrt(userRoleToken.getId());

		// 已存在要保存的记录，更新相关值
		Device dev = this.getDocAppByUserIdAndOs(device.getUserId(),device.getOs());
		if (dev != null) {
			device.setId(dev.getId());
			device.setCreateDt(dev.getCreateDt());
			this.update(device);
		} else {
			// 保存该记录
			if (device.getCreateDt() == null) {
				device.setCreateDt(d);
			}
			this.save(device);
		}
	}

	private void isValidDataForSaveOrUpdate(Device device) {
		isValidData(device);

		String appver = device.getAppver();
		if (StringUtils.isEmpty(appver)) {
			log.error("用户["+device.getUserId()+"-"+device.getOs()+"]端APP版本号过低");
		}
	}


	/**
	 * 数据校验
	 */
	private void isValidData(Device device) {
		// 判断特定值是否为空
		String userId = device.getUserId();
		if (StringUtils.isEmpty(userId)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "userId can't  is null or ''");
		}
		String os = device.getOs();
		if (StringUtils.isEmpty(os)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "os can't  is null or ''");
		}

		String version = device.getVersion();
		if (StringUtils.isEmpty(version)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "version can't  is null or ''");
		}

		String token = device.getToken();
		if (StringUtils.isEmpty(token)) {
			throw new DAOException(DAOException.VALUE_NEEDED, "token can't  is null or ''");
		}

		UserDAO userDAO = DAOFactory.getDAO(UserDAO.class);
		if (!userDAO.exist(userId)) {
			throw new DAOException(404, "User[" + userId + "] not exist");
		}
	}

	@DAOMethod(sql = "from Device where os=:os and token=:token and appid=:appid", orderBy = "lastModify desc")
	public abstract List<Device> findByOsAndTokenAndAppid(@DAOParam("os")String os, @DAOParam("token")String token, @DAOParam("appid")String appid);

	/**
	 * 根据os和token来确定对应的客户端,没有则创建
	 * @param os
	 * @param token
	 * @return
	 */
	@RpcService
	public Device getDeviceByOsAndTokenAndAppid(String os, String token, String appid){
		List<Device> devices = findByOsAndTokenAndAppid(os, token, appid);
		if(devices != null && devices.size() > 0){
			return devices.get(0);
		}
		Device device = new Device();
		device.setOs(os);
		device.setToken(token);
		device.setAppid(appid);
		device.setCreateDt(new Date());
		return save(device);
	}

    /**
     * 上报客户端信息,所有端进入应用之后第一时间立马调用,不需要一定有userId,roleId,urt等
     * OS: 微信端=WX PC端=PC APP端=IOS/Android
     * @param device
     * @return  设备ID,其实相当于sessionID
     */
    @RpcService
	public Device reportDevice(Device device){
		check(device);
        // {{{ ignore those items to updated
        device.setId(null);
        device.setUserId(null);
        device.setRoleId(null);
        device.setUrt(null);
        device.setAccesstoken(null);
        // }}}
        Device d = getDeviceByOsAndTokenAndAppid(device.getOs(), device.getToken(), device.getAppid());
        BeanUtils.copy(device, d);
		d.setStatus("1");
        d.setLastModify(new Date());
        return update(d);
    }

    /**
     * 下线
     * @param id
     */
    @RpcService
    public void offLine(Integer id){
        Device device = get(id);
        if(device != null){
            device.setStatus("0");
            update(device);
        }
    }

	/**
	 * 将设备信息和用户信息关联起来
	 * @param deviceId
	 * @param userId
	 * @param urt
	 * @param accessToken
	 */
	@RpcService
    public void updateDeviceByUserInfo(int deviceId, String userId, int urt, String accessToken){
		UserRoleToken userRoleToken = null;
		try {
			userRoleToken = UserRoleTokenUtils.getByUidAndUrt(userId, urt);
		} catch (SecurityException e) {
			throw new DAOException("getByUidAndUrt failed: uid " + userId + " and urt " + urt);
		}
		Device device = get(deviceId);
		if(device != null){
			device.setUserId(userId);
			device.setUrt(urt);
			device.setAccesstoken(accessToken);
			device.setRoleId(userRoleToken.getRoleId());
			update(device);
		}
	}

	@DAOMethod(sql = "from Device where userId=:userId and urt=:urt and os=:os")
	public abstract List<Device> findByUserIdAndUrtAndOs(@DAOParam("userId") String userId,@DAOParam("urt")int urt,@DAOParam("os")String os);

    @RpcService
	@DAOMethod(sql = "from Device where id = :id")
	public abstract Device getDeviceById(@DAOParam("id") Integer id);

	public void check(Device device){
		if(StringUtils.isEmpty(device.getOs())){	//客户端类型
			throw new DAOException(DAOException.VALUE_NEEDED, "device[client] os missed.");
		}
		if(StringUtils.isEmpty(device.getToken())){	//客户端标识
			throw new DAOException(DAOException.VALUE_NEEDED, "device[client] token missed.");
		}
		if(StringUtils.isEmpty(device.getAppid())){	//应用类型
			throw new DAOException(DAOException.VALUE_NEEDED, "device[client] appid missed.");
		}
	}

}
