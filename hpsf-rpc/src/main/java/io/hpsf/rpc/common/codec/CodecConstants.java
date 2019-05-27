package io.hpsf.rpc.common.codec;

/**
 * 
 * @author winflex
 */
public interface CodecConstants {
	
	int HEADER_LENGTH = 15;
	
	int BODY_LENGTH_OFFSET = 11;
	
	short MAGIC = (short) 0xebab;
}