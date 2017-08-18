package eh.wxpay.dao;

import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.mvc.weixin.entity.OAuthWeixinMPDAO;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.entity.base.Doctor;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.entity.wx.WxSubscribe;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

/**
 * Created by WALY on 2016/4/26.
 */
public abstract class WxSubscribeDAO extends HibernateSupportDelegateDAO<WxSubscribe> {
    private static final Log logger = LogFactory.getLog(WxSubscribeDAO.class);
    public WxSubscribeDAO() {
        super();
        this.setEntityName(WxSubscribe.class.getName());
        this.setKeyField("id");
    }

    /**
     * 如果已存在不保存，不存在会保存
     * @param wxSubscribe
     */
    @RpcService
    public Boolean saveOrNot(WxSubscribe wxSubscribe){
        WxSubscribe sub = getSubscribe(wxSubscribe.getOpenId(), wxSubscribe.getDoctorId(),wxSubscribe.getUserId());
        if(sub==null){
            if(wxSubscribe.getUserId() != null){
                //查看病人是否已经关注医生
                RelationDoctorDAO dao = DAOFactory.getDAO(RelationDoctorDAO.class);
                PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                Patient patient= patientDAO.getByLoginId(wxSubscribe.getUserId());
                if(patient == null) return false;
                //判断是否已关注
                if (!dao.getRelationDoctorFlag(patient.getMpiId(), wxSubscribe.getDoctorId())) {
                    Date date = new Date();
                    wxSubscribe.setCreateTime(date);
                    wxSubscribe.setRelationFlag(false);
                    save(wxSubscribe);
                    return true;
                }
            }else{
                Date date = new Date();
                wxSubscribe.setCreateTime(date);
                wxSubscribe.setRelationFlag(false);
                save(wxSubscribe);
                return true;
            }
        }
        return false;
    }


    @RpcService
    @DAOMethod(sql = "from WxSubscribe where openId=:openId and doctorId =:doctorId and userId = :userId and relationFlag=0")
    public abstract WxSubscribe getSubscribeByOpenIdAndDoctorAndUser(@DAOParam("openId") String openId, @DAOParam("doctorId") Integer doctorId,
                                                                     @DAOParam("userId")String userId);
    /**
     * @param openId
     * @param doctorId
     * @return
     */
    @RpcService
    @DAOMethod(sql = "from WxSubscribe where openId=:openId and doctorId =:doctorId and relationFlag=0")
    public abstract WxSubscribe getSubscribeByOpenIdAndDoctor(@DAOParam("openId") String openId, @DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = "from WxSubscribe where openId=:openId  and relationFlag=0 order by createTime desc")
    public abstract List<WxSubscribe> findSubscribesByOpenId(@DAOParam("openId") String openId, @DAOParam(pageStart = true) int start, @DAOParam(pageLimit = true) int limit);


    @RpcService
    public WxSubscribe getSubscribe(String openId,Integer doctorId,String userId){
        if(StringUtils.isEmpty(userId)){
            return getSubscribeByOpenIdAndDoctor(openId,doctorId);
        }else{
            return getSubscribeByOpenIdAndDoctorAndUser(openId,doctorId,userId);
        }
    }
    /**
     * 更新记录关注状态
     * @param openId
     * @param doctorId
     */
    @RpcService
    @DAOMethod(sql = "update WxSubscribe set relationTime = now(),relationFlag=1,userId=:userId where openId=:openId and doctorId=:doctorId and relationFlag=0")
    public abstract void updateFlag(@DAOParam("openId") String openId,@DAOParam("doctorId") Integer doctorId,@DAOParam("userId") String userId);

    /**
     * 根据openId获取该患者未关注的医生
     * @param openId
     * @return
     */
    @DAOMethod(sql = "from WxSubscribe where relationFlag = 0 and openId=:openId")
    public abstract List<WxSubscribe> findListByOpenIdAndUserId(@DAOParam("openId") String openId);

    @DAOMethod
    public abstract List<WxSubscribe> findByOpenId(String openId,long start,int limit);


    /**
     * 患者关注医生
     * @param appId
     * @param openId
     */
    @RpcService
    public String subscribeDoctorBase(final String appId,final String openId,String userId){
        String result = null;

        final RelationDoctorDAO dao = DAOFactory.getDAO(RelationDoctorDAO.class);
        OAuthWeixinMPDAO opDao = DAOFactory.getDAO(OAuthWeixinMPDAO.class);
        OAuthWeixinMP op = opDao.getByAppIdAndOpenId(appId, openId);
        if(op==null){
            return result;
        }
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        final DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        final String mpiId = patientDAO.getByLoginId(op.getUserId()).getMpiId();
//        final String userId = op.getUserId();
        final List<WxSubscribe> list = findListByOpenIdAndUserId(openId);

        //2016-11-18 17:31:34 zhangx 调试发现该段方法会造成数据库死锁，对其进行优化，已测试能正常使用
//        final HibernateStatelessResultAction<String> action = new AbstractHibernateStatelessResultAction<String>() {
//            @Override
//            public void execute(StatelessSession statelessSession) throws Exception {
//                StringBuffer sb = new StringBuffer();
//                for (WxSubscribe wxSubscribe : list){
//                    Integer doctorId = wxSubscribe.getDoctorId();
//                    Doctor doctor = doctorDAO.getByDoctorId(doctorId);
//                    updateFlag(openId,doctorId,userId);//更新状态
//                    RelationDoctor rd = new RelationDoctor();
//                    rd.setDoctorId(doctorId);
//                    rd.setMpiId(mpiId);
//                    rd.setRelationDate(new Date());
//                    dao.addRelationDoctorForScanCode(rd);//病人关注医生
//                    sb.append(doctor.getName() + "|");
//                }
//
//                setResult(result);
//            }
//        };
//        HibernateSessionTemplate.instance().executeTrans(action);
//        return action.getResult();

        //2017-3-3 15:31:04 zhangx 微信注册成功后，由于未登录，更新关注信息时，取不到userId，报错[参数未给完整],userId为null时，从mp表的记录中取
        final String updateUserId=StringUtils.isEmpty(userId)?op.getUserId():userId;

        StringBuffer sb = new StringBuffer();
        for (final WxSubscribe wxSubscribe : list){
            final HibernateStatelessResultAction<String> action = new AbstractHibernateStatelessResultAction<String>() {
                @Override
                public void execute(StatelessSession statelessSession) throws Exception {
                    Integer doctorId = wxSubscribe.getDoctorId();
                    Doctor doctor = doctorDAO.getByDoctorId(doctorId);
                    updateFlag(openId,doctorId,updateUserId);//更新状态
                    RelationDoctor rd = new RelationDoctor();
                    rd.setDoctorId(doctorId);
                    rd.setMpiId(mpiId);
                    rd.setRelationDate(new Date());
                    dao.addRelationDoctorForScanCode(rd);//病人关注医生
                    setResult(doctor.getName() + "|");
                }
            };
            HibernateSessionTemplate.instance().executeTrans(action);
            sb.append(action.getResult());
        }

        if(sb.indexOf("|")!=-1){
            result = sb.substring(0,sb.length()-1);
        }
        return result;


    }

    /**
     * 关注医生
     */
    @RpcService
    public void relationDoctor(Integer doctorId,String appId,String openId){
        OAuthWeixinMPDAO opDao = DAOFactory.getDAO(OAuthWeixinMPDAO.class);
        OAuthWeixinMP op = opDao.getByAppIdAndOpenId(appId, openId);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        String mpiId = patientDAO.getByLoginId(op.getUserId()).getMpiId();
        updateFlag(openId,doctorId,op.getUserId());//更新状态
        RelationDoctorDAO dao = DAOFactory.getDAO(RelationDoctorDAO.class);
        RelationDoctor rd = new RelationDoctor();
        rd.setDoctorId(doctorId);
        rd.setMpiId(mpiId);
        rd.setRelationDate(new Date());
        dao.addRelationDoctor(rd);//病人关注医生
    }

    /**
     * 扫码关注医生需要一个标记,为避免修改老服务使其他业务走不通，新增一个新接口
     * 2016-12-1 18:25:20 zhangx
     * @param doctorId
     * @param appId
     * @param openId
     */
    @RpcService
    public void relationDoctorForScan(Integer doctorId,String appId,String openId){
        OAuthWeixinMPDAO opDao = DAOFactory.getDAO(OAuthWeixinMPDAO.class);
        OAuthWeixinMP op = opDao.getByAppIdAndOpenId(appId, openId);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        String mpiId = patientDAO.getByLoginId(op.getUserId()).getMpiId();
        updateFlag(openId,doctorId,op.getUserId());//更新状态
        RelationDoctorDAO dao = DAOFactory.getDAO(RelationDoctorDAO.class);
        RelationDoctor rd = new RelationDoctor();
        rd.setDoctorId(doctorId);
        rd.setMpiId(mpiId);
        rd.setRelationDate(new Date());
        dao.addRelationDoctorForScanCode(rd);//病人关注医生
    }
}
