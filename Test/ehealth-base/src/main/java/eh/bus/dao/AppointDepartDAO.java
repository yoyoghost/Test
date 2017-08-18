package eh.bus.dao;

import ctd.dictionary.DictionaryItem;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.*;
import eh.base.service.BusActionLogService;
import eh.entity.base.*;
import eh.entity.bus.AppointDepart;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.ConsultSet;
import eh.entity.his.DepartRequest;
import eh.entity.tx.TxDepart;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.*;

public abstract class AppointDepartDAO extends
        HibernateSupportDelegateDAO<AppointDepart> implements
        DBDictionaryItemLoader<AppointDepart> {

    public static final Logger log = Logger.getLogger(AppointDepartDAO.class);

    public AppointDepartDAO() {
        super();
        this.setEntityName(AppointDepart.class.getName());
        this.setKeyField("appointDepartId");
    }

    /**
     * @author ZX
     */
    @Override
    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(appointDepartId,appointDepartName) from AppointDepart order by appointDepartId")
    public abstract List<DictionaryItem> findAllDictionaryItem(
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * @author ZX
     */
    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(appointDepartId,appointDepartName) from AppointDepart where appointDepartId=:id")
    public abstract DictionaryItem getDictionaryItem(@DAOParam("id") Object key);

    /**
     * 服务名：获取医院挂号科室列表服务
     *
     * @param
     * @return
     * @throws DAOException
     * @author yxq
     */
    @RpcService
    public List<AppointDepart> findByOrganIDAndProfessionCode(
            final int organID, final String professionCode) {
        HibernateStatelessResultAction<List<AppointDepart>> action = new AbstractHibernateStatelessResultAction<List<AppointDepart>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "from AppointDepart where organId=:organID and professionCode like :professionCode and cancleFlag=0");
                Query q = ss.createQuery(hql);
                q.setParameter("organID", organID);
                q.setParameter("professionCode", professionCode + "%");
                List<AppointDepart> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<AppointDepart>) action.getResult();
    }

    @RpcService
    public List<AppointDepart> findAllByOrganIDAndProfessionCode(
            final int organID, final String professionCode) {
        HibernateStatelessResultAction<List<AppointDepart>> action = new AbstractHibernateStatelessResultAction<List<AppointDepart>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "from AppointDepart where organId=:organID and professionCode like :professionCode");
                Query q = ss.createQuery(hql);
                q.setParameter("organID", organID);
                q.setParameter("professionCode", professionCode + "%");
                List<AppointDepart> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<AppointDepart>) action.getResult();
    }


    /**
     * 服务名：根据机构号和科室号查询预约科室
     *
     * @param organID
     * @param departID
     * @return
     * @author Qichengjian
     */
    @RpcService
    public AppointDepart findByOrganIDAndDepartID(final int organID,
                                                  final int departID) {
        HibernateStatelessResultAction<List<AppointDepart>> action = new AbstractHibernateStatelessResultAction<List<AppointDepart>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "from AppointDepart where organId=:organID and departId=:departID");
                Query q = ss.createQuery(hql);
                q.setParameter("organID", organID);
                q.setParameter("departID", departID);
                List<AppointDepart> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        // 只获取查询结果中的一条记录返回
        if ((action.getResult().size() != 0) && (action.getResult() != null)) {
            return action.getResult().get(0);
        }
        return null;
    }

    /**
     * 服务名：根据机构号和科室号查询挂号科室列表
     *
     * @param organID
     * @param departID
     * @return
     * @author luf
     * @date 2017-4-17
     */
    @RpcService
    public List<String> findListByOrganIDAndDepartID(final int organID,
                                                     final int departID) {
        HibernateStatelessResultAction<List<String>> action = new AbstractHibernateStatelessResultAction<List<String>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "select appointDepartCode from AppointDepart where organId=:organID ");
                if (departID!=0) {
                    hql+=" and departId=:departID";
                }
                Query q = ss.createQuery(hql);
                q.setParameter("organID", organID);
                if (departID!=0) {
                    q.setParameter("departID", departID);
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 挂号科室注册服务--hyj
     *
     * @param appointDepart
     */
    @RpcService
    public void saveAppointDepart(AppointDepart appointDepart) {
        if (appointDepart.getOrganId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organId is required");
        }
        if (appointDepart.getAppointDepartCode() == null
                || appointDepart.getAppointDepartCode().equals("")) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "appointDepartCode is required");
        }
        if (appointDepart.getProfessionCode() == null
                || appointDepart.getProfessionCode().equals("")) {
            appointDepart.setProfessionCode("98");
        }
        appointDepart = save(appointDepart);
        BusActionLogService.recordBusinessLog("挂号科室管理",appointDepart.getDepartId()+"","AppointDepart",
                "新增挂号科室【"+appointDepart.getAppointDepartId()+"-"+appointDepart.getAppointDepartName()+"】");
    }

    @RpcService
    @DAOMethod(sql = "from AppointDepart where organId=:organID and appointDepartCode=:appointDepartCode")
    public abstract AppointDepart getByOrganIDAndAppointDepartCode(
            @DAOParam("organID") int organID,
            @DAOParam("appointDepartCode") String appointDepartCode);

    @RpcService
    @DAOMethod(sql = "from AppointDepart where organId=:organID and appointDepartCode=:appointDepartCode")
    public abstract AppointDepart getByOrganIDAndAppointDepartCodeAndProfessionCode(
            @DAOParam("organID") int organID,
            @DAOParam("appointDepartCode") String appointDepartCode);

    @RpcService
    @DAOMethod(sql = "from AppointDepart where organId=:organID and appointDepartCode=:appointDepartCode and cancleFlag=0")
    public abstract AppointDepart getAppointDepartByOrganIDAndAppointDepartCode(
            @DAOParam("organID") int organID,
            @DAOParam("appointDepartCode") String appointDepartCode);

    @RpcService
    public AppointDepart getAppointDepartConcat(String code) {
        final String organId_appointDepartCode = code;
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String("from AppointDepart where CONCAT(organId,'_',appointDepartCode)=:organId_appointDepartCode and cancleFlag=0");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("organId_appointDepartCode", organId_appointDepartCode);
                AppointDepart ad = (AppointDepart) query.uniqueResult();
                setResult(ad);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (AppointDepart) action.getResult();
    }

    @RpcService
    @DAOMethod
    public abstract AppointDepart getById(int id);

    /**
     * 根据挂号科室序号查询挂号科室编码
     *
     * @author ZX
     */
    @RpcService
    public String getAppointDepartCodeById(int id) {
        return this.getById(id).getAppointDepartCode();
    }

    /**
     * 根据挂号科室编码,机构编码查询挂号科室序号
     *
     * @author ZX
     */
    @RpcService
    public int getIdByOrganIdAndAppointDepartCode(int organId,
                                                  String appointDepartCode) {
        return this
                .getByOrganIDAndAppointDepartCode(organId, appointDepartCode)
                .getAppointDepartId();
    }

    /**
     * 根据机构获取医院一级专科代码
     *
     * @author hyj
     */
    @DAOMethod(sql = "select distinct SUBSTRING(professionCode,1,2) from AppointDepart where organId=:organId")
    public abstract List<String> findProfessionCodeByOrganId(
            @DAOParam("organId") int organId);

    /**
     * 根据机构查询挂号科室
     *
     * @param organId
     * @return
     */

    public List<AppointDepart> findAllByOrganId(final int organId) {
        HibernateStatelessResultAction<List<AppointDepart>> action = new AbstractHibernateStatelessResultAction<List<AppointDepart>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "from AppointDepart where organId=:organId and cancleFlag=0");
                Query q = ss.createQuery(hql);
                q.setInteger("organId", organId);
                List<AppointDepart> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 创建普通挂号科室 医生角色。用于预约普通号
     *
     * @param organId
     */
    public void createCommonDeptDoctor(final int organId) {
        List<AppointDepart> list = this.findAllByOrganId(organId);

        for (final AppointDepart ad : list) {
            if (ad.getDepartId() == null) {
                log.info("该科室不存在departId：" + ad.getAppointDepartCode());
                continue;
            }
            final EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);
            Employment empExist = empDao.getByOrganIdAndJobNumber(organId, "PT_" + ad.getAppointDepartCode());
            if (empExist != null) {//已经存在普通账户
                continue;
            }
            createCommonDoctorByAppointDepart(ad);
        }
    }

    public void createCommonDoctorByAppointDepart(final AppointDepart ad) {
        final Integer organId = ad.getOrganId();
        final EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);
        final DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
        final ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);

        HibernateStatelessResultAction<List<AppointDepart>> action = new AbstractHibernateStatelessResultAction<List<AppointDepart>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Doctor doctor = new Doctor();
                doctor.setOrgan(organId);
                doctor.setName(ad.getAppointDepartName());
//				doctor.setName(ad.getAppointDepartName() + "(普通)");
                doctor.setProfession(ad.getProfessionCode());
                doctor.setHaveAppoint(0);
                doctor.setStatus(1);
                doctor.setOrderNum(0);
                doctor.setCreateDt(new Date());
                doctor.setHaveAppoint(0);
                doctor.setVirtualDoctor(true);
                doctor.setChief(0);
                doctor.setGeneralDoctor(1);
                doctor = doctorDao.save(doctor);// 保存医生信息

                Employment emp = new Employment();
                emp.setDoctorId(doctor.getDoctorId());
                emp.setOrganId(organId);
                emp.setJobNumber("PT_" + ad.getAppointDepartCode());
                emp.setDepartment(ad.getDepartId());
                emp = empDao.save(emp);

                ConsultSet consultSet = new ConsultSet();
                consultSet.setDoctorId(doctor.getDoctorId());
                consultSetDAO.save(consultSet);
                log.info("创建普通医生【" + ad.getAppointDepartName() + "(普通)" + "】成功！" + "PT_" + ad.getAppointDepartCode());
            }
        };
        HibernateSessionTemplate.instance().execute(action);
    }

    @DAOMethod(sql = "update AppointDepart set departId=:departId where appointDepartId=:appointDepartId")
    public abstract void updateDepartId(@DAOParam("departId") int departId,
                                        @DAOParam("appointDepartId") int appointDepartId);

    public void addDepartId() {
        List<AppointDepart> list = this.findAllByOrganId(1);

        for (final AppointDepart ad : list) {
            DepartmentDAO depDao = DAOFactory.getDAO(DepartmentDAO.class);
            final Department depart = depDao.getByCodeAndOrgan(
                    ad.getAppointDepartCode(), 1);
            if (depart != null) {
                this.updateDepartId(depart.getDeptId(), ad.getAppointDepartId());
            }
        }
    }

    /**
     * 根据预约科室代码获取预约科室名称
     *
     * @author LF
     * @param appointDepartCode
     * @return
     */
    /*@RpcService
	@DAOMethod(sql = "select appointDepartName from AppointDepart where appointDepartCode=:appointDepartCode")
	public abstract String getAppointDepartNameByAppointDepartCode(
			@DAOParam("appointDepartCode") String appointDepartCode);*/

    /**
     * 为所有机构添加虚拟科室（除去邵逸夫，省中湖滨，省中下沙）
     *
     * @author luf
     */
    @RpcService
    public void addAppointDepartCodeToOrgans() {
        final Integer[] is = {1, 1000017, 1000024};/* ,1000022 */
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "select organId From Organ where status=1");
                for (Integer i : is) {
                    hql.append(" and organId<>");
                    hql.append(i);
                }
                Query q = ss.createQuery(hql.toString());
                @SuppressWarnings("unchecked")
                List<Integer> is = q.list();
                setResult(is);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Integer> organIds = action.getResult();
        // new ArrayList<Integer>();
        // organIds.add(2);
        String appointDepartCodes[] = {"s00001", "s00002", "s00003", "s00004",
                "s00005", "s00006"};
        String appointDepartNames[] = {"内科", "外科", "妇科", "产科", "儿科", "中医科"};
        String professionCodes[] = {"03", "04", "0501", "0502", "07", "50"};
        Date createTime = new Date();
        for (Integer organId : organIds) {
            AppointDepart appointDepart = new AppointDepart();
            appointDepart.setCancleFlag(0);
            appointDepart.setCreateTime(createTime);
            appointDepart.setOrganId(organId);
            for (int i = 0; i < 6; i++) {
                String appointDepartCode = appointDepartCodes[i];
                appointDepart.setAppointDepartCode(appointDepartCode);
                appointDepart.setAppointDepartName(appointDepartNames[i]);
                appointDepart.setProfessionCode(professionCodes[i]);
                AppointDepart appointDepart2 = getAppointDepartByOrganIDAndAppointDepartCode(
                        organId, appointDepartCode);
                if (appointDepart2 == null) {
                    save(appointDepart);
                }
            }
        }
    }


    /**
     * 处理His过来的挂号科室（无则添加）
     */
    @RpcService
    public void dealWithDept(List<TxDepart> depts) {
        if (depts == null || depts.size() == 0) {
            return;
        }
        for (TxDepart dept : depts) {
            AppointDepart appointDept = getAppointDepartByOrganIDAndAppointDepartCode(dept.getOrgan(), dept.getCode());
            if (appointDept == null) {
                //新增 挂号科室
                AppointDepart appointDepart = new AppointDepart();
                appointDepart.setOrganId(dept.getOrgan());
                appointDepart.setAppointDepartCode(dept.getCode());
                appointDepart.setAppointDepartName(dept.getDepartName());
                //appointDepart.setProfessionCode("98");
                appointDepart.setProfessionCode(dept.getCode());
                appointDepart.setCreateTime(new Date());
                appointDepart.setRemarks(dept.getKeshijs());//科室介绍
                save(appointDepart);
                log.info("医院" + dept.getOrgan() + "新增挂号科室" + dept.getCode());
            }
        }
    }

    /**
     * 挂号科室更新
     *
     * @param appointDepart
     * @return
     */
    @RpcService
    public AppointDepart updateAppointDepartForOp(final AppointDepart appointDepart) {
        AppointDepart ad = this.getById(appointDepart.getAppointDepartId());
        if (ad == null) {
//			log.error("Not find the AppointDepart by AppointDepartId:" + appointDepart.getAppointDepartId());
            throw new DAOException("can not find the record by AppointDepartId:" + appointDepart.getAppointDepartId());
        }
        BeanUtils.map(appointDepart, ad);
        update(ad);
        BusActionLogService.recordBusinessLog("挂号科室管理",appointDepart.getDepartId()+"","AppointDepart",
                "更新挂号科室【"+appointDepart.getAppointDepartId()+"-"+appointDepart.getAppointDepartName()+"】");
        return ad;
    }

    @RpcService
    public void regAppointDepartment(AppointDepart d) {
        AppointDepart appointDept = getAppointDepartByOrganIDAndAppointDepartCode(d.getOrganId(), d.getAppointDepartCode());

        if (appointDept == null) {
            //新增 挂号科室
            save(d);
            log.info("医院" + d.getOrganId() + "新增挂号科室" + d.getAppointDepartCode());
        } else {
            log.info("医院" + d.getOrganId() + "挂号科室" + d.getAppointDepartCode() + "已存在");
        }
    }

    @DAOMethod(sql = "select appointDepartCode from AppointDepart where organId=:organId and departId=:departId")
    public abstract  List<String> findByOrganIdAndDepartId(@DAOParam("organId") int OrganId, @DAOParam("departId") int departId);

    /**
     * 获取二级科室跟一级科室对照关系
     * @param organId
     * @return
     */
    @RpcService
    public Map<String,String> getDepartPro(Integer organId){
        log.info("获取二级科室跟一级科室对照关系");
        Map<String,String> map=new HashMap<String,String>();
        DepartmentRelationDAO departmentRelationDAO=DAOFactory.getDAO(DepartmentRelationDAO.class);
        List<DepartmentRelation> list=departmentRelationDAO.findDepartmentRelationByOrganId(organId);
        for (DepartmentRelation d:list){
            map.put(d.getCode(),d.getProfessionCode());
        }
        return map;
    }

    /**
     * 科室信息同步
     * @param departs
     */
    @RpcService
    public void syncAppointDepart(List<DepartRequest> departs){
        log.info("科室信息同步开始-----"+departs.size());
        if(null!=departs && departs.size()>0) {
            //获取行政科室dao操作类
            DepartmentDAO departmentDAO = DAOFactory.getDAO(DepartmentDAO.class);
            //获得原先的行政科室列表
            for (DepartRequest dRequest : departs) {
                //行政科室同步
                Department department=departmentDAO.getByCodeAndOrganAndProfessionCode(dRequest.getDepartCode(),dRequest.getOrganId());
                if (null!=department) {
                    log.info("原有的行政科室信息更新------------");
                    if(null!=dRequest.getDepartName() && !"".equals(dRequest.getDepartName())){
                        department.setName(dRequest.getDepartName());
                    }
                    if (null != dRequest.getRemarks() && !"".equals(dRequest.getRemarks())) {
                        //设置行政科室简介
                        department.setRemarks(dRequest.getRemarks());
                    }
                    departmentDAO.update(department);
                }else{
                    if(!dRequest.getProfessionCode().equalsIgnoreCase("zz")){
                        log.info("没有对应上的行政科室，添加行政科室临时记录"+dRequest.getDepartCode()+"------"+dRequest.getDepartName());
                        department = new Department();
                        department.setCode(dRequest.getDepartCode());
                        department.setName(dRequest.getDepartName());
                        department.setOrganId(dRequest.getOrganId());
                        department.setRemarks(dRequest.getRemarks());
                        department.setProfessionCode(dRequest.getProfessionCode());
                        department.setOrderNum(1000);
                        department.setClinicEnable(1);
                        department.setInpatientEnable(0);
                        department.setStatus(0);
                        departmentDAO.save(department);
                    }else{
                        DepartmentRelationDAO departmentRelationDAO=DAOFactory.getDAO(DepartmentRelationDAO.class);
                        DepartmentRelation d=new DepartmentRelation();
                        d.setOrganId(dRequest.getOrganId());
                        d.setCode(dRequest.getProfessionName());
                        departmentRelationDAO.save(d);
                    }
                }
                //挂号科室同步
                AppointDepart appointDepart=getByOrganIDAndAppointDepartCodeAndProfessionCode(dRequest.getOrganId(),dRequest.getAppointDepartCode());
                if(null!=appointDepart){
                    log.info("原有的挂号科室信息更新------------");
                    if(null!=department && 0<department.getDeptId()){
                        appointDepart.setDepartId(department.getDeptId());
                    }
                    if(null!=dRequest.getAppointDepartName() && !"".equals(dRequest.getAppointDepartName())){
                        appointDepart.setAppointDepartName(dRequest.getAppointDepartName());
                    }
                    update(appointDepart);
                }else{
                    if(!dRequest.getProfessionCode().equalsIgnoreCase("zz")){
                        log.info("没有对应上的挂号科室并且一级科室已对应上，添加挂号科室记录"+dRequest.getAppointDepartCode()+"------"+dRequest.getAppointDepartName());
                        appointDepart= new AppointDepart();
                        appointDepart.setOrganId(dRequest.getOrganId());
                        appointDepart.setAppointDepartCode(dRequest.getAppointDepartCode());
                        appointDepart.setAppointDepartName(dRequest.getAppointDepartName());
                        appointDepart.setDepartId(department.getDeptId());
                        appointDepart.setProfessionCode(dRequest.getProfessionCode());
                        appointDepart.setCancleFlag(0);
                        appointDepart.setOrderNum(1000);
                        save(appointDepart);
                    }
                }
            }
        }
    }

    /**
     * 运营平台--查询未与新增科室对照的挂号科室
     *
     *
     */
    public QueryResult<AppointDepart> queryUnContrastDeparts(final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<AppointDepart>> action = new AbstractHibernateStatelessResultAction<QueryResult<AppointDepart>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "from AppointDepart where cancleFlag=0 and departId is null ");
                Query q = ss.createQuery("select count(*) "+hql);
                long l =(long) q.uniqueResult();
                q = ss.createQuery(hql+" order by organId");
                q.setFirstResult(start);
                q.setMaxResults(limit);
                List<AppointDepart> list = q.list();
                setResult(new QueryResult<AppointDepart>(l,start,limit,list));
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }
}
