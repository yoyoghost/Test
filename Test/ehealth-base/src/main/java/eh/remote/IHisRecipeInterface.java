package eh.remote;

import ctd.util.annotation.RpcService;
import eh.entity.his.*;

/**
 * Created by xuqh on 2016/5/10.
 * zhongzx 改
 */
public interface IHisRecipeInterface{

    /**
     * 处方--发送给His 新增处方
     * @param
     */
    @RpcService
    public void recipeSend(RecipeSendRequest request);

    /**
     * 处方列表查询
     * @param request
     */
    @RpcService
    public RecipeListQueryRes listQuery(RecipeListQueryReq request);

    /**
     * 支付通知到 his
     *
     * @param
     */
    @RpcService
    public PayNotifyRes payNotify(PayNotifyReq request);


    /**
     * 更改取药方式
     * @param request
     * @return
     */
    @RpcService
    public Boolean drugTakeChange(DrugTakeChangeReq request);

    /**
     * 单张处方查询
     * @param request
     * @return
     */
    @RpcService
    public RecipeQueryRes recipeQuery(RecipeQueryReq request);

    @RpcService
    public Boolean recipeUpdate(RecipeStatusUpdateReq request);

    @RpcService
    public DetailQueryRes detailQuery(DetailQueryReq request);

    @RpcService
    public RecipeRefundRes recipeRefund(RecipeRefundReq request);

    @RpcService
    public DrugInfoResponse queryDrugInfo(DrugInfoRequest request);

    @RpcService
    public DrugInfoResponse scanDrugStock(DrugInfoRequest request);
}
