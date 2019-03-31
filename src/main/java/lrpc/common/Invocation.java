package lrpc.common;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 * 
 * @author winflex
 */
public class Invocation implements Serializable {

    private static final long serialVersionUID = -806213717009304249L;

    private String className;
    
    private String methodName;
	
    private Class<?>[] parameterTypes;

    private Object[] paremeters;
    
    private boolean needReply = true;

    private Map<String, String> attachments = new HashMap<String, String>();

    public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getMethodName() {
		return methodName;
	}

	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	public Class<?>[] getParameterTypes() {
		return parameterTypes;
	}

	public void setParameterTypes(Class<?>[] parameterTypes) {
		this.parameterTypes = parameterTypes;
	}

	public Object[] getParemeters() {
		return paremeters;
	}

	public void setParemeters(Object[] paremeters) {
		this.paremeters = paremeters;
	}

	public boolean isNeedReply() {
		return needReply;
	}

	public void setNeedReply(boolean needReply) {
		this.needReply = needReply;
	}

	public Map<String, String> getAttachments() {
		return attachments;
	}

	public void setAttachments(Map<String, String> attachments) {
		this.attachments = attachments;
	}

    @Override
    public String toString() {
        return "Invocation [className=" + className + ", methodName=" + methodName + ", parameterTypes=" + Arrays.toString(parameterTypes)
            + ", paremeters=" + Arrays.toString(paremeters) + ", attachments=" + attachments + "]";
    }
}