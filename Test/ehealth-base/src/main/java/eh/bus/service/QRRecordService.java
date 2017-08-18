package eh.bus.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.QRInfoConstant;
import eh.bus.dao.QRRecordDAO;
import eh.entity.bus.QRRecord;
import eh.entity.wx.WXConfig;
import eh.op.dao.WXConfigsDAO;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

public class QRRecordService {
	private static final Log logger = LogFactory.getLog(QRRecordService.class);
	private static final QRRecordDAO qrRecordDao = DAOFactory.getDAO(QRRecordDAO.class);

	/**
	 * 保存公众号记录
	 * @param appId
	 * @param subscribe
	 * @param openId
	 * @param subscribePublic
     */
	@RpcService
	public void saveQRRecord(String appId,String subscribe,String openId,boolean subscribePublic){
		logger.info("保存扫码记录，公众号["+appId+"]，扫码内容["+subscribe+"]，扫码人["+openId+"],是否关注公众号["+subscribePublic+"]");
		QRRecord record=new QRRecord();

		WXConfigsDAO wxconfigDao=DAOFactory.getDAO(WXConfigsDAO.class);
		WXConfig wxConfig = wxconfigDao.getByAppID(appId);
		Integer wxconfigId=null;

		if (wxConfig!=null) {
			wxconfigId=wxConfig.getId();
		}

		if(wxconfigId==null){
			logger.error("公众号["+appId+"]未配置wxconfig，无法记录数据");
			return;
		}

		//扫码内容为空，
		if(StringUtils.isEmpty(subscribe)){
			record.setQrType(QRInfoConstant.QRTYPE_PUBLIC);
			record.setQrTypeId(wxconfigId);
		}else{
			if(StringUtils.contains(subscribe,"_")){
				record.setQrType(Integer.parseInt(StringUtils.substringBefore(subscribe, "_")));
				record.setQrTypeId(Integer.parseInt(StringUtils.substringAfter(subscribe, "_")));
			}else{
				//当返回字符串长度大于6位时当做带有医生直播间的数据处理
				if(subscribe.length() > 6){
					record.setQrType(QRInfoConstant.QRTYPE_DOCTORID_MATERIA);
				} else {
					record.setQrType(QRInfoConstant.QRTYPE_DOCTOR);
				}
				record.setQrTypeId(Integer.parseInt(subscribe));
			}
		}

		record.setOpenId(openId);
		record.setSubscribePublic(subscribePublic);
		record.setWxConfigId(wxconfigId);
		List<QRRecord> records=qrRecordDao.findEffectiveRecords(record.getQrTypeId(),record.getQrType(),wxconfigId,openId);
		if(records.size()==0){
			record.setRecordStatus(1);
		}else{
			record.setRecordStatus(0);
		}
		qrRecordDao.saveQrRecord(record);
	}

	/**
	 * 取消关注公众号，将扫码关注记录更新成未关注
	 * @param appId
	 * @param openId
     */
	@RpcService
	public void updateRecordStatusByWxConfigIdAndOpenId(String appId,String openId){
		logger.info("base服务器更新扫码记录，公众号["+appId+"],扫码人["+openId+"],取消关注公众号");
		WXConfigsDAO wxconfigDao=DAOFactory.getDAO(WXConfigsDAO.class);
		WXConfig wxConfig = wxconfigDao.getByAppID(appId);
		Integer wxconfigId=null;

		if (wxConfig!=null) {
			wxconfigId=wxConfig.getId();
		}

		if(wxconfigId==null){
			logger.error("公众号["+appId+"]未配置wxconfig，无法记录数据");
			return;
		}

		qrRecordDao.updateRecordStatusByWxConfigIdAndOpenId(wxconfigId,openId);
	}

	/**
	 * 根据qrTypeId获取医生或含医生物料的二维码有效扫码次数
	 * @param qrTypeId
	 * @return
     */
	@RpcService
	public Long getNumByQRTypeId(Integer qrTypeId){
		return qrRecordDao.getNumByQRTypeId(qrTypeId);
	}

	/**
	 * 根据qrTypeId获取二维码扫码信息
	 * @param qrTypeId
	 * @return
     */
	@RpcService
	public List<QRRecord> findEffectiveRecordsByQRTypeId(Integer qrTypeId){
		return qrRecordDao.findEffectiveRecordsByQRTypeId(qrTypeId);
	}
}
