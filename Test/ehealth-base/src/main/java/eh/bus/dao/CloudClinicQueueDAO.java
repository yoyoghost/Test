package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.spring.AppDomainContext;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.base.user.UserSevice;
import eh.bus.constant.CloudClinicSetConstant;
import eh.bus.service.video.RTMService;
import eh.entity.base.Doctor;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.CloudClinicQueue;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.*;

public abstract class CloudClinicQueueDAO extends HibernateSupportDelegateDAO<CloudClinicQueue> {
    public CloudClinicQueueDAO() {
        super();
        this.setEntityName(CloudClinicQueue.class.getName());
        this.setKeyField("id");
    }

    /**
     * 根据申请医生获取所有
     *
     * @param requestDoctor
     * @return
     */
    @DAOMethod(sql = "select d,c From CloudClinicQueue c,Doctor d where c.targetDoctor=d.doctorId and c.requestDoctor=:requestDoctor and DATE_FORMAT(c.createDate,'%y-%m-%d')=DATE_FORMAT(NOW(),'%y-%m-%d') and c.queueStatus=0 order by c.lastModify")
    public abstract List<Object[]> findAllQueueAndTargetByRequest(@DAOParam("requestDoctor") int requestDoctor);

    /**
     * 根据申请医生获取所有
     *
     * @param requestDoctor
     * @return
     */
    @DAOMethod(sql = "From CloudClinicQueue where requestDoctor=:requestDoctor and DATE_FORMAT(createDate,'%y-%m-%d')=DATE_FORMAT(NOW(),'%y-%m-%d') and queueStatus=0 order by lastModify")
    public abstract List<CloudClinicQueue> findAllQueueByRequest(@DAOParam("requestDoctor") int requestDoctor);

    /**
     * 根据目标医生获取所有
     *
     * @param targetDoctor
     * @return
     */
    @DAOMethod(sql = "From CloudClinicQueue where targetDoctor=:targetDoctor and DATE_FORMAT(createDate,'%y-%m-%d')=DATE_FORMAT(NOW(),'%y-%m-%d') and queueStatus=0 order by lastModify")
    public abstract List<CloudClinicQueue> findAllQueueByTarget(@DAOParam("targetDoctor") int targetDoctor);

    /**
     * 根据患者、申请医生、目标医生获取排队列表
     *
     * @param targetDoctor
     * @param requestDoctor
     * @param mpiId
     * @return
     */
    @DAOMethod(sql = "From CloudClinicQueue where targetDoctor=:targetDoctor and requestDoctor=:requestDoctor and " +
            "DATE_FORMAT(createDate,'%y-%m-%d')=DATE_FORMAT(NOW(),'%y-%m-%d') and mpiId=:mpiId and queueStatus=0")
    public abstract List<CloudClinicQueue> findByThree(
            @DAOParam("targetDoctor") int targetDoctor,
            @DAOParam("requestDoctor") int requestDoctor,
            @DAOParam("mpiId") String mpiId);

    /**
     * 我的就诊排队
     *
     * @param doctorId 当前登录医生内码
     * @return HashMap<String, Object>
     */
    public HashMap<String, Object> findQueueOfOut(int doctorId) {
        HashMap<String, Object> result = new HashMap<String, Object>();
        List<CloudClinicQueue> list = new ArrayList<CloudClinicQueue>();
        List<CloudClinicQueue> queues = this.findAllQueueByTarget(doctorId);
        if (null == queues || queues.isEmpty()) {
            return result;
        }
        for (CloudClinicQueue queue : queues) {
//            CloudClinicQueue queue = queues.get(0);
            Doctor reqDoc = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(queue.getRequestDoctor());
            queue.setReDoc(reqDoc);
            Patient p = DAOFactory.getDAO(PatientDAO.class).getByMpiId(queue.getMpiId());
            queue.setPatient(p);
            String telClinicId = queue.getTelClinicId();
            if (!StringUtils.isEmpty(telClinicId)) {
                queue.setClinicStatus(DAOFactory.getDAO(AppointRecordDAO.class).getClinicStatusByTelClinicId(telClinicId));
            }
//            queues.set(0, queue);
            list.add(queue);
        }
//        result.put("queues", queues);
//        result.put("count", queues.size());
        result.put("queues", list);
        result.put("count", list.size());
        return result;
    }

    /**
     * 接诊患者排队
     *
     * @param doctorId 当前登录医生内码
     * @return List<HashMap<String, Object>>
     */
    public List<HashMap<String, Object>> findMyQueueList(int doctorId) {
        List<HashMap<String, Object>> result = new ArrayList<HashMap<String, Object>>();
        List<CloudClinicQueue> queues = this.findAllQueueByRequest(doctorId);
        for (CloudClinicQueue queue : queues) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            Integer targetDoc = queue.getTargetDoctor();
            String mpiId = queue.getMpiId();
            List<CloudClinicQueue> tarQueues = this.findAllQueueByTarget(targetDoc);
            int orderNum = -1;
            for (CloudClinicQueue tarQueue : tarQueues) {
                Integer request = tarQueue.getRequestDoctor();
                orderNum++;
                if (request.equals(doctorId) && mpiId.equals(tarQueue.getMpiId())) {
                    break;
                }
            }
            map.put("queue", queue);
            map.put("orderNum", orderNum);
            result.add(map);
        }
        return result;
    }

    /**
     * 排队信息
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public HashMap<String, Object> findMyAndOutQueues(int doctorId) {
        HashMap<String, Object> map = new HashMap<String, Object>();
        //我的就诊排队
        map.put("out", this.findQueueOfOut(doctorId));
        //接诊患者排队
        map.put("mine", this.findMyQueueList(doctorId));
        return map;
    }

    /**
     * 排队进展
     *
     * @param doctorId 医生内码
     * @return List<CloudClinicQueue>
     */
    @RpcService
    public HashMap<String, Object> queuingProgress(int doctorId) {
        List<CloudClinicQueue> queues = this.findAllQueueByTarget(doctorId);
        HashMap<String, Object> result = new HashMap<String, Object>();
        String mobile = DAOFactory.getDAO(DoctorDAO.class).getMobileByDoctorId(doctorId);
        result.put("mobile", mobile);
        result.put("queues", queues);
        return result;
    }

    /**
     * 排队(纳里云平台)
     *
     * @param queue 排队信息
     * @return
     * @desc 20160913 zhangx 新增小鱼视频平台，将原来的接口进行改造，重新封装
     */
    @RpcService
    public Boolean lineUp(CloudClinicQueue queue) {
        String platformNgari = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI;
        return lineUpByPlatform(queue, platformNgari);
    }

    /**
     * 排队
     *
     * @param queue    排队信息
     * @param platform 视频流平台 值见CloudClinicSetConstant.java
     * @return
     */
    @RpcService
    public Boolean lineUpByPlatform(CloudClinicQueue queue, String platform) {
        if (null == queue) {
            throw new DAOException(DAOException.VALUE_NEEDED, "queue is required!");
        }
        if (StringUtils.isEmpty(queue.getMpiId())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mpiId is required!");
        }
        if (StringUtils.isEmpty(queue.getPatientName())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "patientName is required!");
        }
        if (null == queue.getRequestOrgan()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "requestOrgan is required!");
        }
        if (null == queue.getRequestDoctor()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "requestDoctor is required!");
        }
        if (null == queue.getTargetOrgan()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "targetOrgan is required!");
        }
        if (null == queue.getTargetDoctor()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "targetDoctor is required!");
        }
        if (null == queue.getClinicType()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "clinicType is required!");
        }
        if (StringUtils.isEmpty(queue.getPatientMobile())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "patientName is required!");
        }
        Integer tarDoc = queue.getTargetDoctor();
        Integer reqDoc = queue.getRequestDoctor();
        String mpiId = queue.getMpiId();
        String patientName = queue.getPatientName();
//        List<CloudClinicQueue> queues = this.findAllQueueByTarget(tarDoc);
//        CloudClinicSet ccs = DAOFactory.getDAO(CloudClinicSetDAO.class).getByDoctorIdAndPlatform(tarDoc,platform);
//        if(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI.equals(platform)){
//            //PC端判断
//            if (null != ccs && null == ccs.getOnLineStatus()
//                    && ccs.getOnLineStatus().equals(2) && null == ccs.getFactStatus()
//                    && ccs.getFactStatus().equals(0)) {
//                return false;
//            }
//        }
//
//        if(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU.equals(platform)){
//            //小鱼端判断(离线状态不能排队)
//            if (null != ccs && null == ccs.getOnLineStatus()
//                    && ccs.getOnLineStatus().equals(0) ) {
//                return false;
//            }
//        }


//        if (null == queues && queues.isEmpty()) {
//            return false;
//        }
        List<CloudClinicQueue> queueList = this.findByThree(tarDoc, reqDoc, mpiId);
        if (null != queueList && !queueList.isEmpty()) {
            throw new DAOException(ErrorCode.SERVICE_ERROR,
                    patientName + "患者已加入排队！\n若有其他患者需要排队，请先点击上一步选择患者~");
        }
        queue.setQueueStatus(0);
        queue.setCreateDate(new Date());
        queue.setLastModify(new Date());
        queue.setId(null);
        this.save(queue);
        return true;
    }

    /**
     * 改变排队列表
     *
     * @param id
     * @param flag 标志-0重新排队1踢出排队2取消排队
     */
    @RpcService
    public void changeQueueByIdWithFlag(int id, int flag) {
        CloudClinicQueue queue = this.get(id);
        if (null == queue) {
            throw new DAOException(DAOException.VALUE_NEEDED, "id is required!");
        }
        switch (flag) {
            case 0:
                queue.setLastModify(new Date());
                break;
            case 1:
                queue.setQueueStatus(2);
                break;
            case 2:
                queue.setQueueStatus(9);
        }
        this.update(queue);
    }

    /**
     * 排队信息-添加患者信息
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public HashMap<String, Object> findMyAndOutQueuesWithPatient(int doctorId) {
        HashMap<String, Object> map = new HashMap<String, Object>();
        //我的就诊排队
        map.put("out", this.findQueueOfOutWithPatient(doctorId));
        //接诊患者排队
        map.put("mine", this.findMyQueueListWithPatient(doctorId));
        return map;
    }

    /**
     * 接诊患者排队-添加患者信息
     *
     * @param doctorId 当前登录医生内码
     * @return List<HashMap<String, Object>>
     */
    public List<HashMap<String, Object>> findMyQueueListWithPatient(int doctorId) {
        List<HashMap<String, Object>> result = new ArrayList<HashMap<String, Object>>();
        List<CloudClinicQueue> queues = this.findAllQueueByRequest(doctorId);
        for (CloudClinicQueue queue : queues) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            Integer targetDoc = queue.getTargetDoctor();
            String mpiId = queue.getMpiId();
            List<CloudClinicQueue> tarQueues = this.findAllQueueByTarget(targetDoc);
            int orderNum = -1;
            for (CloudClinicQueue tarQueue : tarQueues) {
                Integer request = tarQueue.getRequestDoctor();
                orderNum++;
                if (request.equals(doctorId) && mpiId.equals(tarQueue.getMpiId())) {
                    break;
                }
            }
            map.put("queue", queue);
            map.put("orderNum", orderNum);
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Patient p = patientDAO.get(queue.getMpiId());
            map.put("patient", p);
            result.add(map);
        }
        return result;
    }

    /**
     * 我的就诊排队
     *
     * @param doctorId 当前登录医生内码
     * @return HashMap<String, Object>
     */
    public HashMap<String, Object> findQueueOfOutWithPatient(int doctorId) {
        HashMap<String, Object> result = new HashMap<String, Object>();
        List<CloudClinicQueue> queues = this.findAllQueueByTarget(doctorId);
        if (null == queues || queues.isEmpty()) {
            return result;
        }

        List<CloudClinicQueue> resultQueues = new ArrayList<CloudClinicQueue>();
        for (CloudClinicQueue queue : queues) {
            Doctor reqDoc = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(queue.getRequestDoctor());
            queue.setReDoc(reqDoc);
            Patient p = DAOFactory.getDAO(PatientDAO.class).getByMpiId(queue.getMpiId());
            queue.setPatient(p);
            String telClinicId = queue.getTelClinicId();
            if (!StringUtils.isEmpty(telClinicId)) {
                queue.setClinicStatus(DAOFactory.getDAO(AppointRecordDAO.class).getClinicStatusByTelClinicId(telClinicId));
            }
            resultQueues.add(queue);
        }

        result.put("queues", resultQueues);
        result.put("count", queues.size());
        return result;
    }

    /**
     * 根据目标医生获取所有和医生在线状态(区分预约在线排序)
     *
     * @param targetDoctor
     * @return
     */
    public List<CloudClinicQueue> findAllQueueByTargetOrderByType(final int targetDoctor, String platform) {
        final HibernateStatelessResultAction<Map<String,Object>> action = new AbstractHibernateStatelessResultAction<Map<String,Object>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);
                List<Object[]> list = new ArrayList<>();
                Map<String,Object> resultMap=new HashMap<String,Object>();
                List<Doctor> docList=new ArrayList<>();
                List<CloudClinicQueue> queues = new ArrayList<CloudClinicQueue>();
                StringBuffer hql = new StringBuffer("select a.*,b.* From bus_CloudClinicQueue a left join bus_AppointRecord b on a.telClinicId=b.telClinicId and b.TelClinicID<>'' where a.targetDoctor=:targetDoctor and date(a.createDate)=date(NOW()) and ((a.clinicType=1 and b.clinicObject=2 and a.queueStatus=0) or (a.clinicType=2 and a.queueStatus=0) or (b.clinicStatus=1 and b.clinicObject=2)) order by a.queueStatus,a.clinicType,b.startTime,a.lastModify");
                Query q = ss.createSQLQuery(hql.toString()).addEntity(CloudClinicQueue.class).addEntity(AppointRecord.class);//实体类映射
                q.setParameter("targetDoctor", targetDoctor);
                list = (List<Object[]>) q.list();
                List<String> telClinicIds=new ArrayList<String>();
                for (Object[] ob : list) {
                    CloudClinicQueue queue = (CloudClinicQueue) ob[0];
                    if (StringUtils.isNotEmpty(queue.getTelClinicId())) {
                        if (telClinicIds.contains(queue.getTelClinicId())) {//去除重复排队记录
                            continue;
                        } else {
                            telClinicIds.add(queue.getTelClinicId());
                        }
                    }
                    docList.add(doctorDAO.getByDoctorId(queue.getRequestDoctor()));
                    if (ob[1] != null) {
                        queue.setAppointRecordId(((AppointRecord) ob[1]).getAppointRecordId());
                        queue.setClinicStatus(((AppointRecord) ob[1]).getClinicStatus());
                    }
                    queues.add(queue);
                }
                resultMap.put("queues",queues);
                resultMap.put("docList",docList);
                setResult(resultMap);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<CloudClinicQueue> queues =(List<CloudClinicQueue>) action.getResult().get("queues");
        List<Doctor> docList=(List<Doctor>) action.getResult().get("docList");
        if (null == queues || queues.isEmpty()) {
            return queues;
        }

        RTMService rtmService = AppDomainContext.getBean("eh.rtmService", RTMService.class);
        //从信令获取视频状态
        List<Map<String, Object>> statusList = rtmService.getOnlineAndFactByDoctorIds(docList,platform);
        List<CloudClinicQueue> resultQueues = new ArrayList<CloudClinicQueue>();
        for (int i = 0; i < queues.size(); i++) {
            Integer oppDoc = queues.get(i).getRequestDoctor();
            UserSevice userSevice = new UserSevice();
            Doctor reqDoc = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(oppDoc);
            reqDoc.setUrtId(userSevice.getDoctorUrtIdByDoctorId(oppDoc));
            queues.get(i).setReDoc(reqDoc);
            Patient p = DAOFactory.getDAO(PatientDAO.class).getByMpiId(queues.get(i).getMpiId());
            queues.get(i).setPatient(p);
            resultQueues.add(queues.get(i));
            Integer fact = 0;
            Integer online = 0;
            if (statusList != null && statusList.size() > 0) {
                fact = (Integer) statusList.get(i).get("fact");
                online = (Integer) statusList.get(i).get("online");
            }
            queues.get(i).setFact(fact);
            queues.get(i).setOnline(online);
        }
        return resultQueues;
    }
}
