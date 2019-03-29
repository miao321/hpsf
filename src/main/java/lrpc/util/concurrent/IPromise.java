package lrpc.util.concurrent;

/**
 * A writeable {@link IFuture}
 * 
 * @author winflex
 */
public interface IPromise extends IFuture {
    
    IPromise setSuccess(Object result);
    
    IPromise setFailure(Throwable cause);
}
