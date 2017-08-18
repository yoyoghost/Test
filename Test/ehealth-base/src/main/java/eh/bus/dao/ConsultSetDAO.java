package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.event.GlobalEventExecFactory;
import eh.base.constant.DoctorWhiteConstant;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganConfigDAO;
import eh.base.service.DoctorWhiteListService;
import eh.bus.constant.ConsultConstant;
import eh.bus.service.ConsultSetService;
import eh.cdr.service.RecipeService;
import eh.entity.base.Doctor;
import eh.entity.base.OrganConfig;
import eh.entity.bus.ConsultSet;
import eh.entity.msg.SmsInfo;
import eh.push.SmsPushService;
import eh.utils.DateConversion;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public abstract class ConsultSetDAO extends HibernateSupportDelegateDAO<ConsultSet> {

    public static final Logger logger = Logger.getLogger(ConsultSetDAO.class);

    public ConsultSetDAO() {
        super();
        this.setEntityName(ConsultSet.class.getName());
        this.setKeyField("doctorId");
    }

    /**
     * 获取医生咨询设置服务
     *
     * @param id --医生编号
     * @return
     */
    @RpcService
    public ConsultSet getById(int id) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        ConsultSet consultSet = get(id);
        if (consultSet == null) {
            consultSet = new ConsultSet();
        }
        try {
            int organId = doctorDAO.get(id).getOrgan();
            OrganConfig organConfig = organConfigDAO.get(organId);
            Double oSignCost = (organConfig.getSignPrice() == null ? 0 : organConfig.getSignPrice()).doubleValue();
            if (oSignCost > consultSet.getSignPrice()) {
                consultSet.setSignPrice(oSignCost);
            }

            //判断机构是否有设置价格
            //wx3.1 2017-5-26 20:17:43 zhangx 机构设置价格(为null表示未设置价格)，则显示机构设置的价格,且不可修改
            ConsultSetService consultSetService = AppContextHolder.getBean("eh.consultSetService", ConsultSetService.class);
            Double organOnlineConsultPrice=consultSetService.getOrganConsultPrice(organId, ConsultConstant.CONSULT_TYPE_GRAPHIC);
            Double organAppointConsultPrice=consultSetService.getOrganConsultPrice(organId, ConsultConstant.CONSULT_TYPE_POHONE);
            Double organRecipeConsultPrice=consultSetService.getOrganConsultPrice(organId, ConsultConstant.CONSULT_TYPE_RECIPE);
            Double organProfessorConsultPrice=consultSetService.getOrganConsultPrice(organId, ConsultConstant.CONSULT_TYPE_PROFESSOR);

            consultSet.setOnLineConsultPrice(Double.valueOf(-1).equals(organOnlineConsultPrice)?consultSet.getOnLineConsultPrice():organOnlineConsultPrice);
            consultSet.setAppointConsultPrice(Double.valueOf(-1).equals(organAppointConsultPrice)?consultSet.getAppointConsultPrice():organAppointConsultPrice);
            consultSet.setRecipeConsultPrice(Double.valueOf(-1).equals(organRecipeConsultPrice)?consultSet.getRecipeConsultPrice():organRecipeConsultPrice);
            consultSet.setProfessorConsultPrice(Double.valueOf(-1).equals(organProfessorConsultPrice)?consultSet.getProfessorConsultPrice():organProfessorConsultPrice);

            consultSet.setCanModifyOnLineConsultPrice(consultSetService.getCanModifyPriceFlag(organOnlineConsultPrice));
            consultSet.setCanModifyAppointConsultPrice(consultSetService.getCanModifyPriceFlag(organAppointConsultPrice));
            consultSet.setCanModifyRecipeConsultPrice(consultSetService.getCanModifyPriceFlag(organRecipeConsultPrice));
            consultSet.setCanModifyProfessorConsultPrice(consultSetService.getCanModifyPriceFlag(organProfessorConsultPrice));

        } catch (Exception e) {
            logger.error("ConsultSetDAO getById return null because this doctor has no consultSet");
        }

        //获取是否显示【专家解读】开关
        DoctorWhiteListService service =AppContextHolder.getBean("doctorWhiteListService", DoctorWhiteListService.class);
        consultSet.setProfessorConsultShowFlag(service.getDoctorWhiteListFlag(DoctorWhiteConstant.WHITELIST_TYPE_PROFESSOR,id));
        return consultSet;
    }



    /**
     * 医生咨询设置新增或修改服务服务--hyj
     *
     * @param consultSet TODO
     */
    @RpcService
    public void addOrupdateConsultSet(ConsultSet consultSet) {
        logger.info("医生咨询设置新增或修改服务服务[addOrupdateConsultSet]:" + JSONUtils.toString(consultSet));
        Boolean onlineFlag = false;
        Boolean appointFlag = false;
        Boolean patientTransferFlag = false;
        Boolean recipeConsultFlag = false;
        if(consultSet==null||consultSet.getDoctorId()==null){
            throw new DAOException(609,"医生咨询设置信息有误");
        }
        final Integer doctorId=consultSet.getDoctorId();
        if (doctorId== null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required");
        }

        ConsultSet target = getDefaultConsultSet(doctorId);

        //暂时不修改医生签约价格（医生端）
        //target有可能为null,将该代码转移到保存，更新前
//        consultSet.setSignPrice(target.getSignPrice());

        if (consultSet != null && target != null) {

            if (consultSet.getOnLineStatus() != null && target.getOnLineStatus() != null
                    && consultSet.getOnLineStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_ON
                    && target.getOnLineStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_OFF) {
                onlineFlag = true;
            }

            if (consultSet.getAppointStatus() != null && target.getAppointStatus() != null
                    && consultSet.getAppointStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_ON
                    && target.getAppointStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_OFF) {
                appointFlag = true;
            }

            if (consultSet.getPatientTransferStatus() != null && target.getPatientTransferStatus() != null
                    && consultSet.getPatientTransferStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_ON
                    && target.getPatientTransferStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_OFF) {
                patientTransferFlag = true;
            }

            if (consultSet.getRecipeConsultStatus() != null && target.getRecipeConsultStatus() != null
                    && consultSet.getRecipeConsultStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_ON
                    && target.getRecipeConsultStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_OFF) {
                RecipeService recipeService = AppContextHolder.getBean("recipeService", RecipeService.class);
                //判断所有执业点能否开处方
                Map<String, Object> resMap = recipeService.openRecipeOrNot(consultSet.getDoctorId());
                boolean result = (boolean)resMap.get("result");
                if(!result){
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "您暂未具有在线续方权限！");
                }
                recipeConsultFlag = true;
            }

            if (consultSet.getSignStatus() != null && target.getSignStatus() != null
                    && consultSet.getSignStatus() == true) {
                if (target.getCanSign() == false) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "您目前没有签约权限");
                }
                if (consultSet.getSignTime() == null || "".equals(consultSet.getSignTime())) {
                    consultSet.setSignTime("1");
                }
            }
        }
        if (target == null) {
            consultSet.setSignPrice(0d);
            if (consultSet.getAppointStatus() == null || consultSet.getAppointStatus() == 0) {
                consultSet.setRemindInTen(false);
            }
            save(consultSet);
        } else {
            consultSet.setSignPrice(target.getSignPrice());

            //判断机构是否有设置价格
            //wx3.1 2017-5-26 20:17:43 zhangx 机构设置价格，则将相关业务价格设置为数据库价格，不进行更新
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            Doctor doctor = doctorDAO.get(doctorId);
            if(doctor!=null){
                ConsultSetService consultSetService = AppContextHolder.getBean("eh.consultSetService", ConsultSetService.class);
                Integer organId=doctor.getOrgan();
                Double organOnlineConsultPrice=consultSetService.getOrganConsultPrice(organId, ConsultConstant.CONSULT_TYPE_GRAPHIC);
                Double organAppointConsultPrice=consultSetService.getOrganConsultPrice(organId, ConsultConstant.CONSULT_TYPE_POHONE);
                Double organRecipeConsultPrice=consultSetService.getOrganConsultPrice(organId, ConsultConstant.CONSULT_TYPE_RECIPE);
                Double organProfessorConsultPrice=consultSetService.getOrganConsultPrice(organId, ConsultConstant.CONSULT_TYPE_PROFESSOR);

                consultSet.setOnLineConsultPrice(
                        Double.valueOf(-1).equals(organOnlineConsultPrice)?consultSet.getOnLineConsultPrice():target.getOnLineConsultPrice());
                consultSet.setAppointConsultPrice(
                        Double.valueOf(-1).equals(organAppointConsultPrice)?consultSet.getAppointConsultPrice():target.getOnLineConsultPrice());
                consultSet.setRecipeConsultPrice(
                        Double.valueOf(-1).equals(organRecipeConsultPrice)?consultSet.getRecipeConsultPrice():organRecipeConsultPrice);
                consultSet.setProfessorConsultPrice(
                        Double.valueOf(-1).equals(organProfessorConsultPrice)?consultSet.getProfessorConsultPrice():organProfessorConsultPrice);

            }


            BeanUtils.map(consultSet, target);
            if (target.getAppointStatus() == null || target.getAppointStatus() == 0) {
                target.setRemindInTen(false);
            }
            if (consultSet.getEndTime1() == null) {
                target.setEndTime1(null);
            }
            if (consultSet.getEndTime2() == null) {
                target.setEndTime2(null);
            }
            if (consultSet.getEndTime3() == null) {
                target.setEndTime3(null);
            }
            if (consultSet.getEndTime4() == null) {
                target.setEndTime4(null);
            }
            if (consultSet.getEndTime5() == null) {
                target.setEndTime5(null);
            }
            if (consultSet.getEndTime6() == null) {
                target.setEndTime6(null);
            }
            if (consultSet.getEndTime7() == null) {
                target.setEndTime7(null);
            }
            if (consultSet.getStartTime1() == null) {
                target.setStartTime1(null);
            }
            if (consultSet.getStartTime2() == null) {
                target.setStartTime2(null);
            }
            if (consultSet.getStartTime3() == null) {
                target.setStartTime3(null);
            }
            if (consultSet.getStartTime4() == null) {
                target.setStartTime4(null);
            }
            if (consultSet.getStartTime5() == null) {
                target.setStartTime5(null);
            }
            if (consultSet.getStartTime6() == null) {
                target.setStartTime6(null);
            }
            if (consultSet.getStartTime7() == null) {
                target.setStartTime7(null);
            }
            update(target);

            //打开或者关闭业务设置开关的时候，异步计算排序值
            GlobalEventExecFactory.instance().getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    doctorDAO.updateDoctorSearchRating(doctorId);
                }
            });
        }

        List<Integer> typeList = new ArrayList<Integer>();
        //发送图文咨询开通推送
        if (onlineFlag) {
            typeList.add(1);
        }

        //发送电话咨询开通推送
        if (appointFlag) {
            typeList.add(2);
        }

        //发送特需开通推送
        if (patientTransferFlag) {
            typeList.add(0);
        }
        //发送寻医问药开通推送
        if (recipeConsultFlag) {
            typeList.add(3);
        }
        if (typeList.size() > 0) {
            sendWxTemplateMsgToCorrespondingPatient(consultSet.getDoctorId(), typeList);
        }
    }

    /**
     * 医生咨询设置新增或修改服务服务（运营平台用）
     *
     * @param consultSet TODO
     */
    @RpcService
    public void addOrupdateConsultSetAdmin(ConsultSet consultSet) {
        logger.info("医生咨询设置新增或修改服务服务[addOrupdateConsultSet]:" + JSONUtils.toString(consultSet));
        Boolean onlineFlag = false;
        Boolean appointFlag = false;
        Boolean patientTransferFlag = false;
        Boolean recipeConsultFlag = false;
        if(consultSet==null){
            throw new DAOException(DAOException.VALUE_NEEDED, "consultSet is required");
        }
        final  Integer doctorId=consultSet.getDoctorId();
        if (doctorId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required");
        }
        ConsultSet target = getDefaultConsultSet(doctorId);

        if (consultSet != null && target != null) {
            if (consultSet.getOnLineStatus() != null && target.getOnLineStatus() != null
                    && consultSet.getOnLineStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_ON
                    && target.getOnLineStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_OFF) {
                onlineFlag = true;
            }

            if (consultSet.getAppointStatus() != null && target.getAppointStatus() != null
                    && consultSet.getAppointStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_ON
                    && target.getAppointStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_OFF) {
                appointFlag = true;
            }

            if (consultSet.getPatientTransferStatus() != null && target.getPatientTransferStatus() != null
                    && consultSet.getPatientTransferStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_ON
                    && target.getPatientTransferStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_OFF) {
                patientTransferFlag = true;
            }
            if (consultSet.getRecipeConsultStatus() != null && target.getRecipeConsultStatus() != null
                    && consultSet.getRecipeConsultStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_ON
                    && target.getRecipeConsultStatus() == ConsultConstant.DOCTOR_SERVICE_SWITCH_OFF) {
                recipeConsultFlag = true;
            }
        }
        if (target == null) {
            if (consultSet.getAppointStatus() == null || consultSet.getAppointStatus() == 0) {
                consultSet.setRemindInTen(false);
            }
            save(consultSet);
        } else {
            BeanUtils.map(consultSet, target);
            if (target.getAppointStatus() == null || target.getAppointStatus() == 0) {
                target.setRemindInTen(false);
            }
            if (consultSet.getEndTime1() == null) {
                target.setEndTime1(null);
            }
            if (consultSet.getEndTime2() == null) {
                target.setEndTime2(null);
            }
            if (consultSet.getEndTime3() == null) {
                target.setEndTime3(null);
            }
            if (consultSet.getEndTime4() == null) {
                target.setEndTime4(null);
            }
            if (consultSet.getEndTime5() == null) {
                target.setEndTime5(null);
            }
            if (consultSet.getEndTime6() == null) {
                target.setEndTime6(null);
            }
            if (consultSet.getEndTime7() == null) {
                target.setEndTime7(null);
            }
            if (consultSet.getStartTime1() == null) {
                target.setStartTime1(null);
            }
            if (consultSet.getStartTime2() == null) {
                target.setStartTime2(null);
            }
            if (consultSet.getStartTime3() == null) {
                target.setStartTime3(null);
            }
            if (consultSet.getStartTime4() == null) {
                target.setStartTime4(null);
            }
            if (consultSet.getStartTime5() == null) {
                target.setStartTime5(null);
            }
            if (consultSet.getStartTime6() == null) {
                target.setStartTime6(null);
            }
            if (consultSet.getStartTime7() == null) {
                target.setStartTime7(null);
            }
            update(target);

            //打开或者关闭业务设置开关的时候，异步计算排序值
            GlobalEventExecFactory.instance().getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);
                    doctorDAO.updateDoctorSearchRating(doctorId);
                }
            });
        }

        List<Integer> typeList = new ArrayList<Integer>();
        //发送图文咨询开通推送
        if (onlineFlag) {
            typeList.add(1);
        }

        //发送电话咨询开通推送
        if (appointFlag) {
            typeList.add(2);
        }

        //发送特需开通推送
        if (patientTransferFlag) {
            typeList.add(0);
        }
        //发送寻医问药开通推送
        if (recipeConsultFlag) {
            typeList.add(3);
        }
        if (typeList.size() > 0) {
            sendWxTemplateMsgToCorrespondingPatient(consultSet.getDoctorId(), typeList);
        }
    }

    /**
     * 发送推送消息
     *
     * @param doctorId
     * @param busType  0特需预约；1图文咨询；2电话咨询; 3寻医问药
     * @return {{first.DATA}}
     * 医生姓名：{{keyword1.DATA}}
     * 开通类型：{{keyword2.DATA}}
     * 开通时间：{{keyword3.DATA}}
     * {{remark.DATA}}
     */
    public boolean sendWxTemplateMsgToCorrespondingPatient(Integer doctorId, List<Integer> recommendTypes) {
        logger.info("医生doctorId[" + doctorId + "]开通业务设置[" + recommendTypes + "],发送消息");
        Doctor doc = DAOFactory.getDAO(DoctorDAO.class).get(doctorId);

        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusId(doctorId);
        smsInfo.setBusType("BusOpened");
        smsInfo.setSmsType("BusOpened");
        smsInfo.setOrganId(doc.getOrgan());
        smsInfo.setClientId(null);
        smsInfo.setCreateTime(new Date());
        smsInfo.setStatus(0);
        smsInfo.setExtendValue(JSONUtils.toString(recommendTypes));

        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(smsInfo);
        return true;
    }

    @DAOMethod(limit = 1000, sql = "from Doctor d where d.doctorId not in (select doctorId from ConsultSet)")
    public abstract List<Doctor> findDoctor();

    /**
     * 供 查询医生关注医生列表(分页,业务筛选) 调用（findRelationDoctorListStartAndLimitBus）
     *
     * @param doctorId
     * @param busId
     * @return
     * @author LF
     */
    @RpcService
    public ConsultSet getConsultByDoctorAndBus(final int doctorId,
                                               final int busId) {
        HibernateStatelessResultAction<ConsultSet> action = new AbstractHibernateStatelessResultAction<ConsultSet>() {
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer buffer = new StringBuffer(
                        "From ConsultSet where doctorId=:doctorId and ");
                switch (busId) {
                    case 1:
                        buffer.append("transferStatus=1");
                        break;
                    case 2:
                        buffer.append("meetClinicStatus=1");
                        break;
                    case 3:
                        buffer.append("(onLineStatus=1 or appointStatus=1)");
                        break;
                    default:
                        buffer.append("1=1");
                        break;
                }
                Query q = ss.createQuery(buffer.toString());
                q.setParameter("doctorId", doctorId);
                ConsultSet consultSet = (ConsultSet) q.uniqueResult();
                setResult(consultSet);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 整理数据，将ConsultSet表中没有记录的添加上去
     *
     * @author zhangx
     * @date 2015-11-6 下午2:39:25
     */
    public void cleanData() {
        HibernateStatelessResultAction<ConsultSet> action = new AbstractHibernateStatelessResultAction<ConsultSet>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer buffer = new StringBuffer(
                        "From Doctor where virtualDoctor=0 and doctorId>=7531");
                Query q = ss.createQuery(buffer.toString());
                List<Doctor> list = q.list();

                for (Doctor target : list) {
                    if (getById(target.getDoctorId()) == null) {
                        ConsultSet set = new ConsultSet();
                        set.setDoctorId(target.getDoctorId());
                        set.setOnLineStatus(0);
                        set.setAppointStatus(0);
                        set.setTransferStatus(0);
                        set.setMeetClinicStatus(0);
                        set.setPatientTransferStatus(0);
                        ConsultSetDAO setDao = DAOFactory
                                .getDAO(ConsultSetDAO.class);
                        setDao.save(set);
                    }
                }
            }
        };
        HibernateSessionTemplate.instance().execute(action);
    }

    /**
     * 供 getEffConsultTime 调用（consultdao）
     *
     * @param consultDate 咨询时间
     * @param doctorId    医生内码
     * @return Object[] --0开始时间 1结束时间 2时间间隔
     * @author luf
     */
    public Object[] getThreeByDoctorAndDate(final Date consultDate,
                                            final Integer doctorId) {
        HibernateStatelessResultAction<Object[]> action = new AbstractHibernateStatelessResultAction<Object[]>() {
            public void execute(StatelessSession ss) throws DAOException {
                int week = DateConversion.getWeekOfDateInt(consultDate);
                StringBuffer hql = new StringBuffer("select ");
                String search = "startTime" + week + ",endTime" + week;
                hql.append(search);
                hql.append(",intervalTime From ConsultSet where doctorId=:doctorId");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                Object[] items = (Object[]) q.uniqueResult();
                setResult(items);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 获取默认的医生设置
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public ConsultSet getDefaultConsultSet(int doctorId) {
        ConsultSet set = get(doctorId);
        if (set == null) {
            return null;
        }

        // 获取图文咨询，在线咨询，特需预约价格及设置
        set.setOnLineStatus(set.getOnLineStatus() == null ? 0 : set
                .getOnLineStatus());
        set.setOnLineConsultPrice(set.getOnLineConsultPrice() == null ? 0d
                : set.getOnLineConsultPrice());
        set.setAppointStatus(set.getAppointStatus() == null ? 0 : set
                .getAppointStatus());
        set.setAppointConsultPrice(set.getAppointConsultPrice() == null ? 0d
                : set.getAppointConsultPrice());
        set.setPatientTransferStatus(set.getPatientTransferStatus() == null ? 0
                : set.getPatientTransferStatus());
        set.setPatientTransferPrice(set.getPatientTransferPrice() == null ? 0d
                : set.getPatientTransferPrice());
        set.setAppointDays(set.getAppointDays() == null ? 0 : set
                .getAppointDays());

        //新增专家解读-寻医问药默认设置
        set.setProfessorConsultStatus(set.getProfessorConsultStatus() == null ? 0 : set.getProfessorConsultStatus());
        set.setProfessorConsultPrice(set.getProfessorConsultPrice() == null ? 0 : set.getProfessorConsultPrice());
        set.setRecipeConsultStatus(set.getRecipeConsultStatus() == null ? 0 : set.getRecipeConsultStatus());
        set.setRecipeConsultPrice(set.getRecipeConsultPrice() == null ? 0 : set.getRecipeConsultPrice());


        set.setSignStatus(set.getSignStatus() == null ? false : set.getSignStatus());
        set.setSignPrice(set.getSignPrice() == null ? 0 : set.getSignPrice());

        return set;
    }

    /**
     * 根据 organId 修改医生是否支持签约功能
     *
     * @param canSign
     * @param organId
     */
    @RpcService
    @DAOMethod(sql = "update ConsultSet set canSign=:canSign,signStatus=:canSign where doctorId in ( select doctorId from Doctor where organ=:organId )")
    public abstract void updateCanSignByDoctorId(@DAOParam("canSign") Boolean canSign, @DAOParam("organId") Integer organId);


    /**
     * 运营平台开启允许医生签约权限
     *
     * @param canSign
     * @param doctorId
     */
    @RpcService
    @DAOMethod(sql = "update ConsultSet set canSign=:canSign,signStatus=:canSign where doctorId= :doctorId )")
    public abstract void updateCanSignOP(@DAOParam("canSign") Boolean canSign, @DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = "update ConsultSet set recipeConsultStatus=:recipeConsultStatus,recipeConsultPrice=:recipeConsultPrice where doctorId=:doctorId )")
    public abstract void updateRecipeConsultInfoByDoctorId(@DAOParam("recipeConsultStatus") Integer recipeConsultStatus,@DAOParam("recipeConsultPrice") Double recipeConsultPrice, @DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = "update ConsultSet set professorConsultStatus=:professorConsultStatus,professorConsultPrice=:professorConsultPrice where doctorId=:doctorId )")
    public abstract void updateProfessorConsultInfoByDoctorId(@DAOParam("professorConsultStatus") Integer professorConsultStatus,@DAOParam("professorConsultPrice") Double professorConsultPrice, @DAOParam("doctorId") Integer doctorId);

    /**
     * 判断医生 是否未开通所有业务
     * zhongzx
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public boolean hasBusOpen(Integer doctorId) {
        ConsultSet consultSet = get(doctorId);
        if (null == consultSet) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "没有找到医生的配置类");
        }
        //电话咨询
        Integer appointStatus = consultSet.getAppointStatus();
        Integer meetClinicStatus = consultSet.getMeetClinicStatus();
        //图文咨询
        Integer onLineStatus = consultSet.getOnLineStatus();
        Integer patientTransferStatus = consultSet.getPatientTransferStatus();
        Integer transferStatus = consultSet.getTransferStatus();
        Boolean signStatus = consultSet.getSignStatus();
        boolean bl = false;
        if (null != appointStatus && 1 == appointStatus) {
            bl = true;
        } else if (null != meetClinicStatus && 1 == meetClinicStatus) {
            bl = true;
        } else if (null != onLineStatus && 1 == onLineStatus) {
            bl = true;
        } else if (null != patientTransferStatus && 1 == patientTransferStatus) {
            bl = true;
        } else if (null != transferStatus && 1 == transferStatus) {
            bl = true;
        } else if (null != signStatus && signStatus) {
            bl = true;
        }
        return bl;
    }
    /**
     * 获取的是开通咨询业务的医生
     * @return
     * @author cuill
     */
    public List<Integer> queryOpenConsultionDoctor() {
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>(){
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {

                StringBuilder hql = new StringBuilder("SELECT doctorId FROM D WHERE onLineStatus = 1 " +
                        "OR appointStatus = 1 OR professorConsultStatus = 1 OR recipeConsultStatus = 1");
                //后期可能会加上在线续方
                Query query = statelessSession.createQuery(hql.toString());
                List<Integer> doctorList = query.list();
                setResult(doctorList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     *  批量查询配置表
     * @return List<ConsultSet>
     * @author jiangtf
     */
    @DAOMethod (sql = "from ConsultSet where doctorId in :doctorIds")
    public abstract List<ConsultSet> findByDoctorIds(@DAOParam("doctorIds") List<Integer> doctorIds);
}
