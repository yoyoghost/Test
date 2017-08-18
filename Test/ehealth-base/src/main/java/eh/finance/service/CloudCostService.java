package eh.finance.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessAction;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DepartmentDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganDAO;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.MeetClinicDAO;
import eh.bus.dao.MeetClinicResultDAO;
import eh.bus.dao.OrganCloudPriceDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicResult;
import eh.entity.bus.OrganCloudPrice;
import eh.entity.finance.CloudCost;
import eh.entity.mpi.Patient;
import eh.finance.dao.CloudCostDAO;
import eh.mpi.dao.PatientDAO;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.util.*;

/**
 * @author jianghc
 * @create 2017-04-26 14:54
 **/
public class CloudCostService {
    private CloudCostDAO cloudCostDAO;
    private OrganCloudPriceDAO organCloudPriceDAO;
    private DoctorDAO doctorDAO;
    private AppointRecordDAO appointRecordDAO;
    private OrganDAO organDAO;
    private MeetClinicResultDAO meetClinicResultDAO;
    private MeetClinicDAO meetClinicDAO;
    private PatientDAO patientDAO;
    private DepartmentDAO departmentDAO;

    public CloudCostService() {
        this.cloudCostDAO = DAOFactory.getDAO(CloudCostDAO.class);
        this.organCloudPriceDAO = DAOFactory.getDAO(OrganCloudPriceDAO.class);
        this.doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        this.appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        this.organDAO = DAOFactory.getDAO(OrganDAO.class);
        this.meetClinicResultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
        this.meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        this.patientDAO = DAOFactory.getDAO(PatientDAO.class);
        this.departmentDAO = DAOFactory.getDAO(DepartmentDAO.class);
    }

    /**
     * 云门诊记账
     *
     * @param billID
     */
    @RpcService
    public void accountAppointCloudAccounting(Integer billID) {
        if (billID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "billID is require");
        }
        final AppointRecord appointRecord = appointRecordDAO.get(billID);
        if (appointRecord == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "billId is not exist");
        }
        final HibernateStatelessAction action = new HibernateStatelessAction() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                if (appointRecord.getOffLineAccount() != null && !appointRecord.getOffLineAccount().equals(Integer.valueOf(0))) {
                    throw new DAOException(" bill is account");
                }
                Doctor doctor = doctorDAO.getByDoctorId(appointRecord.getOppdoctor());
                OrganCloudPrice price = organCloudPriceDAO.getPriceForAppoint(appointRecord.getOppOrgan(), doctor.getProTitle());
                appointRecord.setOffLinePrice(price == null ? Double.valueOf(0) : price.getPrice());
                appointRecord.setOffLineAccount(1);
                CloudCost cloudCost = appointRecordToCloudCost(appointRecordDAO.update(appointRecord));
                cloudCost.setPayType(1);
                cloudCostDAO.save(cloudCost);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    /**
     * 云门诊线上支付记账
     * @param billID
     * @param price
     */
    @RpcService
    public void accountAppointCloudAccountingOnLine(Integer billID,final Double price) {
        if (billID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "billID is require");
        }
        if(price==null){
            throw new DAOException(DAOException.VALUE_NEEDED, "price is require");
        }
        final AppointRecord appointRecord = appointRecordDAO.get(billID);
        if (appointRecord == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "billId is not exist");
        }
        final HibernateStatelessAction action = new HibernateStatelessAction() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                if (appointRecord.getOffLineAccount() != null && !appointRecord.getOffLineAccount().equals(Integer.valueOf(0))) {
                    throw new DAOException(" bill is account");
                }
                appointRecord.setOffLinePrice(price);
                appointRecord.setOffLineAccount(1);
                CloudCost cloudCost = appointRecordToCloudCost(appointRecord);
                appointRecord.setOffLinePrice(new Double(-1)); //-1是线上支付线上记账
                appointRecordDAO.update(appointRecord);
                cloudCost.setPayType(1);
                cloudCostDAO.save(cloudCost);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }



    @RpcService
    public void unAccountAppointCloudAccounting(final Integer billID, final String returnsReason, final String returnsMemo) {
        if (billID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "billID is require");
        }
        final AppointRecord appointRecord = appointRecordDAO.get(billID);
        if (appointRecord == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "billId is not exist");
        }
        final HibernateStatelessAction action = new HibernateStatelessAction() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                if (!ObjectUtils.nullSafeEquals(appointRecord.getOffLineAccount(), 1)) {
                    throw new DAOException(" bill is not account");
                }
                CloudCost cloudCost = cloudCostDAO.getAppointCostByBillId(billID);
                cloudCost.setId(null);
                cloudCost.setPrice(-(cloudCost.getPrice() == null ? 0 : cloudCost.getPrice()));
                cloudCost.setCreateDate(new Date());
                cloudCost.setPayType(2);
                cloudCost.setReturnsReason(returnsReason);
                cloudCost.setReturnsMemo(returnsMemo);
                cloudCostDAO.save(cloudCost);
                appointRecord.setOffLineAccount(-1);
                appointRecordDAO.update(appointRecord);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    private CloudCost appointRecordToCloudCost(AppointRecord appointRecord) {
        CloudCost cloudCost = new CloudCost();
        cloudCost.setBussType("appoint");
        cloudCost.setBillId(appointRecord.getAppointRecordId());
        cloudCost.setPrice(appointRecord.getOffLinePrice());
        cloudCost.setMpiId(appointRecord.getMpiid());
        cloudCost.setPatientName(appointRecord.getPatientName());
        cloudCost.setPatientCard(appointRecord.getCertId());
        Integer applyOrganId = Integer.valueOf(appointRecord.getAppointOragn());
        cloudCost.setApplyOrganId(applyOrganId);

        Organ applyOrgan = organDAO.getByOrganId(applyOrganId);
        if (applyOrgan != null) {
            cloudCost.setApplyOrganName(applyOrgan.getName());
            cloudCost.setApplyOrganArea(applyOrgan.getAddrArea());
        }

        Integer applyDoctorId = Integer.valueOf(appointRecord.getAppointUser());
        cloudCost.setApplyDoctorId(applyDoctorId);
        cloudCost.setApplyDoctorName(appointRecord.getAppointName());
        cloudCost.setBaseOrganId(appointRecord.getOrganId());
        Organ baseOrgan = organDAO.getByOrganId(appointRecord.getOrganId());
        if (baseOrgan != null) {
            cloudCost.setBaseOrganName(baseOrgan.getName());
            cloudCost.setBaseOrganArea(baseOrgan.getAddrArea());
        }
        cloudCost.setBaseDepartment(appointRecord.getAppointDepartId());
        cloudCost.setBaseDepartmentName(appointRecord.getAppointDepartName());
        cloudCost.setBaseDoctorId(appointRecord.getDoctorId());
        cloudCost.setBaseDoctorName(doctorDAO.getNameById(appointRecord.getDoctorId()));
        cloudCost.setSuperiorOrganId(appointRecord.getOppOrgan());
        Organ superiorOrgan = organDAO.getByOrganId(appointRecord.getOppOrgan());
        if (baseOrgan != null) {
            cloudCost.setSuperiorOrganName(superiorOrgan.getName());
            cloudCost.setSuperiorOrganArea(baseOrgan.getAddrArea());
        }
        cloudCost.setSuperiorDepartment(appointRecord.getOppdepart());
        cloudCost.setSuperiorDepartmentName(appointRecord.getOppdepartName());
        cloudCost.setSuperiorDoctorId(appointRecord.getOppdoctor());
        cloudCost.setSuperiorDoctorName(doctorDAO.getNameById(appointRecord.getOppdoctor()));
        cloudCost.setDiagnosis(appointRecord.getSummary());
        cloudCost.setCreateDate(new Date());
        return cloudCost;
    }

    @RpcService
    public void accountMeetClinicCloudAccounting(Integer billID) {
        if (billID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "billID is require");
        }

        final MeetClinic meetClinic = meetClinicDAO.getByMeetClinicId(billID);
        if (meetClinic == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "billId is not exist");
        }
        final List<MeetClinicResult> results = meetClinicResultDAO.findByMeetOrderByReport(billID);
        if (results == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "billId is not exist");
        }
        final HibernateStatelessAction action = new HibernateStatelessAction() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                if (meetClinic.getOffLineAccount() != null && !meetClinic.getOffLineAccount().equals(0)) {
                    throw new DAOException(" bill is account");
                }
                CloudCost cloudCost = meetClinicToCloudCost(meetClinic);
                Map<Integer, OrganCloudPrice> priceMap = new HashMap<Integer, OrganCloudPrice>();
                Integer minCost = null;
                List<CloudCost> costList = new ArrayList<CloudCost>();
                for (MeetClinicResult result : results) {
                    CloudCost cc = new CloudCost();
                    BeanUtils.map(cloudCost,cc);
                    Doctor doctor = doctorDAO.getByDoctorId(result.getTargetDoctor());
                    OrganCloudPrice price = organCloudPriceDAO.getPriceByOrganAndBussTypeAndProTitle(result.getTargetOrgan(), "meet", doctor.getProTitle());
                    cc = meetClinicResultToCloudCost(cc, result);
                    cc.setSuperiorDoctorName(doctor.getName());
                    cc.setPrice(0.0);
                    priceMap.put(result.getMeetClinicResultId(), price);
                    if (price != null) {
                        if (minCost == null || price.getPrice().compareTo(priceMap.get(minCost).getPrice()) < 0) {
                            minCost = result.getMeetClinicResultId();
                        }
                    }
                     costList.add(cc);
                }
                double totalPrice = 0.0;
                for (CloudCost cc : costList) {
                    OrganCloudPrice ocp = priceMap.get(cc.getSubBillId());
                    if (ocp != null) {
                        if (!cc.getSubBillId().equals(minCost)) {
                            cc.setPrice(ocp.getExtPrice());
                            totalPrice += ocp.getExtPrice().doubleValue();
                        } else {
                            cc.setPrice(ocp.getPrice());
                            totalPrice += ocp.getPrice().doubleValue();
                        }
                    }
                    cc.setPayType(1);//缴费
                    cloudCostDAO.save(cc);
                }
                meetClinic.setOffLinePrice(totalPrice);
                meetClinic.setOffLineAccount(1);
                meetClinicDAO.update(meetClinic);

            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    @RpcService
    public void unAccountMeetClinicCloudAccounting(final Integer billID, final String returnsReason, final String returnsMemo) {
        if (billID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "billID is require");
        }
        final MeetClinic meetClinic = meetClinicDAO.getByMeetClinicId(billID);
        if (meetClinic == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "billId is not exist");
        }
        if (!ObjectUtils.nullSafeEquals(meetClinic.getOffLineAccount(), 1)) {
            throw new DAOException(" bill is not account");
        }
        final HibernateStatelessAction action = new HibernateStatelessAction() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                List<CloudCost> costList = cloudCostDAO.findMeetCostByBillId(billID);
                if (costList == null) {
                    return;
                }
                for (CloudCost cloudCost : costList) {
                    cloudCost.setId(null);
                    cloudCost.setPrice(-(cloudCost.getPrice() == null ? 0 : cloudCost.getPrice()));
                    cloudCost.setCreateDate(new Date());
                    cloudCost.setPayType(2);
                    cloudCost.setReturnsReason(returnsReason);
                    cloudCost.setReturnsMemo(returnsMemo);
                    cloudCostDAO.save(cloudCost);
                }
                meetClinic.setOffLineAccount(-1);
                meetClinicDAO.update(meetClinic);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }


    @RpcService
    public QueryResult<CloudCost> queryCloudCostByDateAndOrgan(Integer superiorOrganId, Date startDate, Date endDate, String bussType,
                                                               Integer baseOrganId, String superiorDepartment, Integer payType, int start, int limit) {
        return cloudCostDAO.queryCloudCostByDateAndOrgan(superiorOrganId, startDate, endDate, bussType, baseOrganId, superiorDepartment, payType, start, limit);
    }


    private CloudCost meetClinicToCloudCost(MeetClinic meetClinic) {
        CloudCost cloudCost = new CloudCost();
        cloudCost.setBussType("meet");
        cloudCost.setBillId(meetClinic.getMeetClinicId());
        cloudCost.setMpiId(meetClinic.getMpiid());
        Patient patient = patientDAO.getByMpiId(meetClinic.getMpiid());
        cloudCost.setPatientName(patient.getPatientName());
        cloudCost.setPatientCard(patient.getCardId());
        Integer applyOrganId = meetClinic.getRequestOrgan();
        cloudCost.setApplyOrganId(applyOrganId);
        Organ organ = organDAO.getByOrganId(applyOrganId);
        if(organ!=null){
            cloudCost.setApplyOrganName(organ.getName());
            cloudCost.setBaseOrganName(organ.getName());
            cloudCost.setApplyOrganArea(organ.getAddrArea());
            cloudCost.setBaseOrganArea(organ.getAddrArea());
        }
        Integer applyDoctorId = meetClinic.getRequestDoctor();
        String deptName = departmentDAO.getNameById(meetClinic.getRequestDepart());
        cloudCost.setApplyDepartment(meetClinic.getRequestDepart()+"");
        cloudCost.setApplyDepartmentName(deptName);
        cloudCost.setBaseOrganId(applyOrganId);

        cloudCost.setBaseDepartment(meetClinic.getRequestDepart()+"");
        cloudCost.setBaseDepartmentName(deptName);
        String docName = doctorDAO.getNameById(applyDoctorId);
        cloudCost.setBaseDoctorId(applyDoctorId);
        cloudCost.setBaseDoctorName(docName);
        cloudCost.setApplyDoctorName(docName);
        cloudCost.setApplyDoctorId(applyDoctorId);
        cloudCost.setDiagnosis(meetClinic.getDiagianName());
        cloudCost.setCreateDate(new Date());
        return cloudCost;
    }


    private CloudCost meetClinicResultToCloudCost(CloudCost cloudCost, MeetClinicResult result) {
        cloudCost.setSubBillId(result.getMeetClinicResultId());
        cloudCost.setSuperiorOrganId(result.getTargetOrgan());

        Organ organ = organDAO.getByOrganId(result.getTargetOrgan());
        if(organ!=null){
            cloudCost.setSuperiorOrganName(organ.getName());
            cloudCost.setSuperiorOrganArea(organ.getAddrArea());
        }
        cloudCost.setSuperiorDepartment(result.getTargetDepart() + "");
        cloudCost.setSuperiorDepartmentName(result.getTargetDepart() == null ? "" : departmentDAO.getNameById(result.getTargetDepart()));
        cloudCost.setSuperiorDoctorId(result.getTargetDoctor());

        return cloudCost;
    }


}
