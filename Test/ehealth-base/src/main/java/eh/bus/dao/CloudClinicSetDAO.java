package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.bus.constant.CloudClinicSetConstant;
import eh.bus.service.CloudClinicSetService;
import eh.entity.bus.CloudClinicSet;

/**
 * update方法只保留-登陆/登出时切换onlineStatus为不接受云门诊，切换医生接收状态时相应的修改OnlineStaus值
 */
public abstract class CloudClinicSetDAO extends
        HibernateSupportDelegateDAO<CloudClinicSet> {


    public CloudClinicSetDAO() {
        super();
        this.setEntityName(CloudClinicSet.class.getName());
        this.setKeyField("setId");
    }

    /**
     * 仅供cloudclinicsetservice.addOrUpdateSet调用
     *
     * @param doctorId
     * @param platform
     * @return
     */
    @RpcService
    @DAOMethod
    public abstract CloudClinicSet getByDoctorIdAndPlatform(int doctorId, String platform);

//	@DAOMethod
//	public abstract List<CloudClinicSet> findByDoctorId(int doctorId);

//    @DAOMethod
//    public abstract void updateFactStatusByDoctorId(Integer factStatus,
//                                                    Integer doctorId);

//    @DAOMethod
//    public abstract void updateFactStatusByDoctorIdAndPlatform(Integer factStatus,
//                                                               Integer doctorId, String platform);

//    @DAOMethod
//    public abstract void updateOnLineStatusByDoctorId(Integer onLineStatus,
//                                                      Integer doctorId);

    @DAOMethod
    public abstract void updateOnLineStatusByDoctorIdAndPlatform(Integer onLineStatus,
                                                                 Integer doctorId, String platform);

    @RpcService
    @DAOMethod(sql = "update CloudClinicSet set onLineStatus=0,factStatus=0 where doctorId=:doctorId")
    public abstract void updateAllOnLineToOffByDoctorId(@DAOParam("doctorId") int doctorId);

    /**
     * 获取一个医生的在线/视频状态(纳里云平台)
     *
     * @param doctorId
     * @return
     * @author zhangx
     * @date 2015-12-29 下午4:57:49
     * @desc 20160913 zhangx 新增小鱼视频平台，将原来的接口进行改造，重新封装
     * @date 2017-2-20 luf：pc2.9版本后不用此方法，此方法只兼容老版本使用
     */
    @RpcService
    public CloudClinicSet getDoctorSet(int doctorId) {
        CloudClinicSetService service = AppContextHolder.getBean("cloudClinicSetService", CloudClinicSetService.class);
        return service.getDoctorSetByPlatform(doctorId, CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI);
    }


    /**
     * 更新在线状态(纳里云平台)
     *
     * @param onLineStatus 0不上线；1暂时离开；2我在线上
     * @param doctorId     要更新的医生ID
     * @author zhangx
     * @date 2015-12-29 下午3:57:45
     * @desc 20160913 zhangx 新增小鱼视频平台，将原来的接口进行改造，重新封装
     */
    @RpcService
    public void updateOnlineStatus(Integer onLineStatus, Integer doctorId) {
        CloudClinicSetService service = AppContextHolder.getBean("cloudClinicSetService", CloudClinicSetService.class);
        String platformNgari = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI;
        service.updatePlatformOnlineStatus(onLineStatus, doctorId, platformNgari);
    }

    /**
     * 更新接诊方、出诊方的视频状态(纳里云平台)
     *
     * @param factStatus 0视频空闲；1视频中
     * @param docId      当前医生ID
     * @param oppDocId   对方医生ID
     * @param fromFlag   0在线云门诊；1预约云门诊
     * @author zhangx
     * @date 2015-12-29 下午3:50:33
     * @desc 20160913 zhangx 新增小鱼视频平台，将原来的接口进行改造，重新封装
     */
    @RpcService
    public Boolean updateFactStatus(final Integer factStatus,
                                    final Integer docId, final Integer oppDocId, Integer fromFlag) {
        String platformNgari = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI;
        CloudClinicSetService service = AppContextHolder.getBean("cloudClinicSetService", CloudClinicSetService.class);
        return service.updatePlatformFactStatus(factStatus, docId, oppDocId, fromFlag, platformNgari, platformNgari);
    }

    /**
     * 判断对方医生是否可呼叫(纳里云平台)
     *
     * @param oppDocId 对方医生ID
     * @param fromFlag 0在线云门诊；1预约云门诊
     * @author zhangx
     * @date 2016-1-4 下午2:12:21
     * @desc 20160913 zhangx 新增小鱼视频平台，将原来的接口进行改造，重新封装
     */
    @RpcService
    public void canCall(Integer oppDocId, Integer fromFlag) {
        CloudClinicSetService service = AppContextHolder.getBean("cloudClinicSetService", CloudClinicSetService.class);
        service.canCallByPlatform(oppDocId, fromFlag, CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI);
    }

    /**
     * 设置医生的默认在线云门诊状态数据
     *
     * @param doctorId
     * @desc 20160913 zhangx 新增小鱼视频平台，将原来的接口进行改造，重新封装
     */
    @RpcService
    public void updateDocSet(Integer doctorId) {
//		CloudClinicSetService service= AppContextHolder.getBean("cloudClinicSetService",CloudClinicSetService.class);
//		String platformNgari=CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI;
//		service.updateDocSetByPlatform(doctorId,platformNgari);
    }

    /**
     * 共定时服务调用
     */
    @RpcService
    @DAOMethod(sql = "update CloudClinicSet set onLineStatus=0,FactStatus=0 where platform='ngari'")
    public abstract void updateAllStatusToOff();

}
