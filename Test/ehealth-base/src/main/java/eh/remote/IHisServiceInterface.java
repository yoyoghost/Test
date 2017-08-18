package eh.remote;

import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.entity.bus.*;
import eh.entity.cdr.LabReport;
import eh.entity.his.*;
import eh.entity.his.fee.*;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.his.hisCommonModule.HisResponseForBase;
import eh.entity.his.hisCommonModule.Patient_HIS;
import eh.entity.his.push.callNum.PushRequestModel;
import eh.entity.his.push.message.CustomMessage;
import eh.entity.his.sign.SignCommonBean;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.HisHealthRecord;
import eh.entity.mpi.Patient;

import java.util.List;
import java.util.Map;

public interface IHisServiceInterface {
	/**
	 * 预约和门诊转诊预约
	 * @param appointmentrequest
	 */
	@RpcService
	public void registAppoint(AppointmentRequest appointmentrequest);
//	@RpcService
//	public boolean cancelAppoint(String appointID,String cancelResean,String organId);
	/**
	 * 取消预约
	 * @param request
	 * @return
	 */
	@RpcService
	public boolean cancelAppoint(AppointCancelRequest request);
	@RpcService
	public Object regist();
	@RpcService
	public String testRpc();
	@RpcService
	Object echo(Object o);
	@RpcService
	public Patient queryPatientInfo(Patient patient);
	/**
	 * 住院转诊预约
	 * @param request
	 */
	@RpcService
	public void registInHosAppoint(AppointInHosRequest request);
	
	/**
	 * 事件处理完成通知服务
	 * @param messageID
	 */
	@RpcService
	public void notifyHisSucc(String messageID);
	
	/**
	 * 采集检验报告
	 * @param patient
	 * @param organ
	 */
	@RpcService
	public void queryLabReport(Patient patient,Integer organ);
	
	/**
	 *	武义转诊申请
	 * @author ZX
	 * @date 2015-7-8  下午6:13:17
	 * @param appointmentrequest
	 */
	@RpcService
	public void registTransfer(MedRequest request);
	
	/**
	 * 医院的就诊记录导入到平台
	 * @author Qichengjian
	 * @date 2015-7-31
	 */
	@RpcService
	public void loadClinicRecordRequest(CliniclistRequest request);
	
	/**
	 * 医院门诊病历导入到平台
	 * @author Qichengjian
	 * @param request
	 */
	@RpcService
	public void saveClinicEmrRequest(ClinicEmrRequest request);
	/**
	 * 武义医保备案取消服务
	 * @param transferId
	 * @throws DAOException
	 */
	@RpcService
	public boolean cancelMedResult(Integer transferId) throws DAOException;

	/**
	 * 调用省平台接口更新号源 //TODO 效率问题  时间是否会过长，影响用户体验。
	 * */
	@RpcService
	public List<AppointSource> getAppointsourceByDoctor(String ysid);
	
	/**
	 * 医技检查--预约
	 * */
	@RpcService
	public void registCheckrequest(AppointmentRequest request);
	/**
	 * 医技检查--取消预约
	 * */
	@RpcService
	public boolean cancelCheck(AppointCancelRequest req);
	
	/**
	 * 医技检查--推送报告任务
	 * */
	@RpcService
	public  TaskQueue saveTaskQueue(TaskQueue queue);

	/**
	 * 取报告单
	 * */
	@RpcService
	public List<LabReport> getLableReports(AppointmentRequest request);

	/**
	 * 取报告单详情
	 * */
	@RpcService
	public String getReportDetail(Map<String,String> m );

	@RpcService
	public List<DoctorDateSource> getDoctorScheduling(HisDoctorParam doctor);

	@RpcService
	public List<DoctorDateSource> getScheulingForHealth(HisDoctorParam doctor);

	/**
	 * zhongzx
	 * 查询当前就诊顺序数
	 * @param departCode
	 * @param jobNumber
     * @return
     */
	@RpcService
	public Integer queryOrderNum(String departCode, String jobNumber);

	/**
	 * 查询医院缴费记录
	 * @param request
	 * @return
     */
	@RpcService
	public List<OutpatientListResponse> getPayList(OutpatientListRequest request);

	/**
	 * 查询缴费详情
	 * @param request
	 * @return
     */
	@RpcService
	public List<OutpatientDetailResponse> getPayDetail(OutpatientDetailRequest request);

	/**
	 * 查询住院预交
	 * @param request
	 * @return
	 */
	@RpcService
	public InHospitalPrepaidResponse getInHospitalPrepaidInfo(InHospitalPrepaidRequest request);

	/**
	 * 查询住院预交日清单列表
	 * @param request
	 * @return
	 */
	@RpcService
	public List<Map<String,Object>> getAlreadyGenerateDayList(AlreadyGenerateCostRequest request);

	/**
	 * 查询住院预交日清单列表
	 * @param request
	 * @return
	 */
	@RpcService
	public Map<String,Object> getAlreadyGenerateDayListNew(AlreadyGenerateCostRequest request);


	/**
	 * 查询住院预交按类型查看
	 * @param request
	 * @return
	 */
	@RpcService
	public List<AlreadyGenerateCostResponseType> getAlreadyGenerateTypeList(AlreadyGenerateCostRequest request);

	/**
	 * 查询住院预交按类型查看明细
	 * @param request
	 * @return
	 */
	@RpcService
	public Map<String, Object> getAlreadyGenerateTypeDetail(AlreadyGenerateCostRequest request);

	/**
	 * zhongzx
	 * 支付
	 * @param pay
	 * @return
     */
	@RpcService
	public Map<String, Object> payToHis(PayResultNotifyToHis pay);


	/**
	 * 当天挂号申请
	 * @param req
	 * @return
	 */
	@RpcService
	public void registAppointToday(AppointmentRequest req);

	/**
	 * 是否允许预约
	 * @param sign
     */
	@RpcService
	public HisResponseForBase isCanSign(SignCommonBean sign);
	@RpcService
	public boolean registSign(SignCommonBean sign);
	@RpcService
	public List<SignCommonBean> getSignList(SignCommonBean sign);

	/**
	 * 当日门诊缴费预算
	 * @param preSettlementRequest
	 * @return
     */
	@RpcService
	public OutpatientPreSettlementResponse queryPreSettlement(OutpatientPreSettlementRequest preSettlementRequest);

	/**
	 * 当日门诊缴费结算
	 * @param settlementRequest
	 * @return
     */
	@RpcService
	public OutpatientSettlementResponse querySettlement(OutpatientSettlementRequest settlementRequest,String certID);

	@RpcService
	public HisResponse<MakeBillResponse> regAccount(MakeBillRequest request);

	@RpcService
	public HisResponse<PreBillResponse> regPreAccount(PreBillRequest request);

	@RpcService
	public HisResponse remoteImageApply(TaskQueue taskQueue);

	@RpcService
	public HisResponse<String> cancelRegAccount(CancelRegAccount canecl);

	@RpcService
	public HisResponse<InpPreResponse> inpPrePayment(InpPreRequest req);

	/**
	 * 病人影像查询服务入口
	 */
	@RpcService
	public void getImageInfo();

	@RpcService
	public QueryOrderNumResponse queryOrderNumNew(QueryOrderNumRequest request);

	@RpcService
	public HisResponse sendCustomMessage(PushRequestModel<CustomMessage> requestModel);
	
	@RpcService
	public HisResponse<InpPreResponse> hospitalPre(InpPreRequest req);

	@RpcService
	public HisResponse queryPaymentResult(PaymentResultRequest paymentResultRequest);

	/**
	 * 健康档案同步接口(his-->base)
	 * @param hisHealthRecord
	 * @return
	 */
	@RpcService
	public  HisResponse<Patient>  synHealthRecord(HisHealthRecord hisHealthRecord);

	/**
	 * 健康档案同步接口(base-->his)
	 * @param patient
	 * @return
	 */
	@RpcService
	public  HisResponse<Patient>  uploadHealthRecordToHis(Patient patient);

	@RpcService
	public HisResponse<List<QueryHisAppointRecordResponse>> queryHisAppointRecords(QueryHisAppointRecordParam param);

	@RpcService
	public HisResponse sendHealthCards(List<HealthCard> healthCardList);

	@RpcService
	public HisResponse sendPatient(Patient_HIS patient);
}
