package eh.remote;

import ctd.util.annotation.RpcService;
import eh.entity.cdr.EsignPerson;

import java.util.Map;

public interface IESignService {

	/**
	 * e签宝平台添加用户
	 * @param person  e签宝平台注册信息
	 * @return
     */
	@RpcService
	public String addPerson(EsignPerson person) throws Exception;

	/**
	 *  开方医生签名服务
	 * @param userId   医生在e签宝账户
	 * @param map      生成处方单参数信息
     * @return
     */
	@RpcService
	public byte[] signRecipePDF(String userId, Map<String,Object> map);

	/**
	 * 药师签名服务
	 * @param map  包括以下key-value
	 * userId   String
	 * fileName String
	 * data     byte[]
	 * @return
     */
	@RpcService
	public byte[] signForChemist(Map<String, Object> map);

	/**
	 * 远程门诊报告单签名
	 * @param map
     * @return
     */
	@RpcService
	public byte[] signForTelClinic(Map<String, Object> map);

	/**
	 * 创建个人印章
	 * @param accountId
	 * @param imgB64
     * @return
     */
	@RpcService
	public String createPersonalSealData(String accountId, String imgB64);

	/**
	 * 会诊报告单签名服务
	 * @param map
	 * @return
	 * @throws Exception
     */
	@RpcService
	public byte[] signForMeetClinic(Map<String, Object> map) throws Exception;

	/**
	 * 转诊单生成pdf
	 * @param map
	 * @return
	 * @throws Exception
     */
	@RpcService
	public byte[] createTransferPdf(Map<String, Object> map) throws Exception;
}
