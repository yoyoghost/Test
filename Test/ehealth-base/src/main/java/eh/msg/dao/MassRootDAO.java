package eh.msg.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.PagingInfo;
import eh.base.dao.DoctorDAO;
import eh.entity.mpi.Patient;
import eh.entity.msg.Article;
import eh.entity.msg.Mass;
import eh.entity.msg.MassRoot;
import eh.entity.msg.SessionMessage;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.msg.service.CustomContentService;
import eh.msg.service.MsgPushService;
import eh.msg.service.SessionDetailService;
import eh.push.SmsPushService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.Assert;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public abstract class MassRootDAO extends HibernateSupportDelegateDAO<MassRoot> {

	// 医生APP显示名字
	private static final String DOCTOR_APP_NAME = "纳里医生";

	public MassRootDAO() {
		super();
		this.setEntityName(MassRoot.class.getName());
		this.setKeyField("id");
	}

	/**
	 * 群发短信V1.0
	 * 
	 * @author zhangx
	 * @date 2015-9-28下午9:12:58
	 * @param doctorId
	 *            发起群发短信功能的医生ID
	 * @param mpiIds
	 *            群发短信的目标患者
	 * @param msg
	 *            群发短信内容
	 */

	/**
	 * 群发短信V2.0 AngryKitty
	 * 
	 * @date 2015-10-09
	 * @param doctorId
	 *            发起群发短信功能的医生ID
	 * @param msg
	 *            短信内容
	 * @param allFlag
	 *            是否全选
	 * @param type
	 *            所属分组
	 * @param labelName
	 *            标签名
	 * @param checkList
	 *            群发短信的目标患者
	 * @param unCheckList
	 *            群发短信不要发送的患者
	 * 
	 */
	@RpcService
	public void sendMassMsg(int doctorId, String msg, boolean allFlag,
			int type, String labelName, List<String> checkList,
			List<String> unCheckList) {
		DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
		PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
		MassDAO massDao = DAOFactory.getDAO(MassDAO.class);
		RelationDoctorDAO relationDoctorDao = DAOFactory
				.getDAO(RelationDoctorDAO.class);
		if (!docDao.exist(doctorId)) {
			throw new DAOException(609, "不存在该医生");
		}
		Integer organ = docDao.getOrganByDoctorId(doctorId);
        List<String> rds = new ArrayList<>();
        if (allFlag) {// 全选
            switch (type) {// 组别
            case 0:// 全部患者
                rds = relationDoctorDao.findByDoctorId(doctorId);
                break;
            case 1:// 签约患者
                rds = relationDoctorDao.findSignPatientByDoctorId(doctorId);
                break;
            case 2:// 未分组患者
                rds = relationDoctorDao.findNoGroupByDoctorId(doctorId);
                break;
            case 3:// 指定标签患者
                rds = relationDoctorDao.findByDoctorIdAndLabel(doctorId,
                        labelName);
                break;
			default:
				break;
            }

            if (CollectionUtils.isNotEmpty(rds) && CollectionUtils.isNotEmpty(unCheckList)) {
                rds.removeAll(unCheckList);
            }
        } else {// 非全选
            if(null != checkList && !checkList.isEmpty()){
                rds = checkList;
            }
        }

        if(CollectionUtils.isEmpty(rds)){
            return;
        }

        MassRoot root = new MassRoot();
        root.setReqDoctor(doctorId);
        root.setCreateTime(new Date());
        root.setContent(msg);
        root.setSendFlag(false);
        root.setMassType(MassRoot.MESSAGE);
        root.setSendNumber(rds.size());
        MassRoot returnRoot = this.save(root);
        Integer rootId = returnRoot.getId();

        Mass mass = null;
        for(String mpiId : rds) {
            if(patDao.exist(mpiId)) {
                mass = new Mass(rootId, mpiId, false);
                massDao.save(mass);
            }
        }

		AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(rootId, organ, "SendSmsToPatient", "", 0);
	}


	/**
	 * 随访 医生群发短信
	 * @param doctorId
	 * @param msg
	 * @param mpiIdList
     */
	@RpcService
	public void sendMassMsgToPatient(int doctorId, String msg, List<String> mpiIdList) {
		if(null == mpiIdList){
			throw new DAOException(DAOException.VALUE_NEEDED, "mpiIdList is needed");
		}
		DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
		PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
		MassDAO massDao = DAOFactory.getDAO(MassDAO.class);
		if (!docDao.exist(doctorId)) {
			throw new DAOException(609, "不存在该医生");
		}
		Integer organ=docDao.getOrganByDoctorId(doctorId);
		MassRoot root = new MassRoot();
		root.setReqDoctor(doctorId);
		root.setCreateTime(new Date());
		root.setContent(msg);
		root.setSendFlag(false);
        root.setMassType(MassRoot.MESSAGE);
        root.setSendNumber(mpiIdList.size());
		MassRoot returnRoot = this.save(root);
		Integer rootId = returnRoot.getId();
		if (rootId != null) {
				for (String mpiId : mpiIdList) {
					if (patDao.exist(mpiId)) {
						Mass mass = new Mass();
						mass.setRootId(rootId);
						mass.setMpiId(mpiId);
						mass.setFlag(false);
						massDao.save(mass);
					}
				}
		} else {
			throw new DAOException(609, "短信发送失败！");
		}

		AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(rootId, organ, "SendSmsToPatient", "", 0);
	}

    /**
     * 发送群发记录会话详情 (app医生端 消息功能展示)
     * @param massRootId
     * @param doctorTel
     * @param msg
     * @param massType
     * @param total
     */
    public void sendSessionDetailForMass(Integer massRootId, String doctorTel, String msg, Integer massType, int total){
        Assert.hasLength(doctorTel,"sendSessionDetailForMass doctorTel is null");

        SessionDetailService sessionDetailService = AppContextHolder.getBean("sessionDetailService",SessionDetailService.class);
        String massTitle = "";
        if(MassRoot.MESSAGE == massType) {
            massTitle = "向" + total + "位患者发送短信";
        }else if(MassRoot.INFORMATION == massType){
            massTitle = "向"+total+"位患者分享资讯";
        }
        sessionDetailService.addSysTextMsgMassToTarDoc(massRootId,doctorTel,massTitle,msg,false,false);
    }

	/**
	 * 根据Id获取群发短信记录
	 * 
	 * @author zhangx
	 * @date 2015-9-29上午10:56:59
	 * @param id
	 *            群发短信记录主键
	 * @return 群发短信记录
	 */
	@RpcService
	@DAOMethod
	public abstract MassRoot getById(int id);

	/**
	 * 群发短信以后，进行回写数据库，组装推送消息
	 * 
	 * @author zhangx
	 * @date 2015-9-29下午3:01:03
	 * @param mass
	 *            群发短信记录
	 * @throws InterruptedException
	 */
	@RpcService
	public void onMassMsgSended(MassRoot root) throws InterruptedException {

		DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
		MassDAO massDao = DAOFactory.getDAO(MassDAO.class);

		// 医生手机号
		String docTel = docDao.getByDoctorId(root.getReqDoctor()).getMobile();
		if (StringUtils.isEmpty(docTel)) {
			return;
		}

		List<Patient> sPatient = massDao.findSumByRootId(root.getId(), true);
		List<Patient> ePatient = massDao.findSumByRootId(root.getId(), false);

		int success = 0;
		int error = 0;
		if (sPatient != null) {
			success = sPatient.size();
		}
		if (ePatient != null) {
			error = ePatient.size();
		}

		// 系统消息内容
		String strMsg = "";
		StringBuilder detailMsg = new StringBuilder("成功发出").append(success)
				.append("条短信息,失败").append(error).append("条。");
		if (error != 0) {
			detailMsg.append("发出失败的记录如下：");
			for (Patient patient : ePatient) {
				detailMsg.append(patient.getMobile()).append(",");
			}
			strMsg = detailMsg.substring(0, detailMsg.length() - 1)
					+ "。请核对联系方式是否正确";
		} else {
			strMsg = detailMsg.toString();
		}
		// 新增系统消息
		SessionDetailDAO sessionDetailDAO = DAOFactory
				.getDAO(SessionDetailDAO.class);

		// 构建系统消息
		SessionMessage sessionMsg = new SessionMessage();
		sessionMsg.setToUserId(docTel);
		sessionMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
		sessionMsg.setMsgType("text");

		List<Article> list = new ArrayList<Article>();

		Article art = new Article();
		art.setContent(strMsg);
		art.setTitle("系统提醒");
		art.setUrl("");
		list.add(art);

		sessionMsg.setArticles(list);

		// 新增系统消息
		sessionDetailDAO.addSysMessageByUserId(JSONUtils.toString(sessionMsg),
				1, "eh", docTel);



		// 推送消息内容
		String msg = "您有一条系统消息";
		HashMap<String,Object> msgCustom= CustomContentService.getSystemCustomContent();
		MsgPushService.pushMsgToDoctor(docTel,msg,msgCustom);

	}
	
	/**
	 * 更新主表
	 * @param root
	 *               主表信息
	 */
	@RpcService
	public void updateMassRoot(MassRoot root) {
		this.update(root);
		if (root.getMassType().equals(MassRoot.MESSAGE)){
			DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);
			Integer organ=doctorDAO.getOrganByDoctorId(root.getReqDoctor());
			AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(root.getId(), organ, "PushSmsFaildAndSuccess", "", 0);
		}
	}

    /**
     * 查询所有群发消息，分页
     * @param doctorId
     * @param pagingInfo
     * @return
     */
    public List<MassRoot> findAllWithPage(final int doctorId, final PagingInfo pagingInfo){
        HibernateStatelessResultAction<List<MassRoot>> action = new AbstractHibernateStatelessResultAction<List<MassRoot>>() {
            public void execute(StatelessSession ss) throws DAOException {
                String hql = "from MassRoot where reqDoctor=:doctorId order by id desc ";
                Query q = ss.createQuery(hql);
                q.setParameter("doctorId",doctorId);
                if(null != pagingInfo){
                    q.setFirstResult(pagingInfo.getCurrentIndex());
                    q.setMaxResults(pagingInfo.getLimit());
                }
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
	
}
