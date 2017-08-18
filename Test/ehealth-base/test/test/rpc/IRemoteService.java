package test.rpc;

import ctd.util.annotation.RpcService;

public interface IRemoteService {

	@RpcService
	public void reTryAppoint(int appointRecordId);
}
