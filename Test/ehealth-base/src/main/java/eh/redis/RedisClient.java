package eh.redis;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;

@Component("redisClient")
public class RedisClient {

	@Resource(name = "redisTemplate")
	private RedisTemplate redisTemplate;
	private static RedisClient instance;
	private RedisClient(){
		instance = this;
	}
	public static RedisClient instance(){
		if (instance == null){
			instance = new RedisClient();
		}
		return instance;
	}

	public RedisTemplate getRedisTemplate() {
		return redisTemplate;
	}

	//用于设置hash结构数据的ttl
	private Set<String> keySet = new HashSet<>();

	public <T> T hget(final String name,final String field) {
		return (T) redisTemplate.execute(new RedisCallback<T>() {
			public T doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> keySerializer = redisTemplate.getKeySerializer();
				RedisSerializer<T> valueSerializer = redisTemplate.getValueSerializer();
				byte[] name_ = keySerializer.serialize(name);
				byte[] field_ = keySerializer.serialize(field);
				byte[] value_ = connection.hGet(name_,field_);
				return (T) valueSerializer.deserialize(value_);
			}
		});
	}
	public <T> void hset(final String name,final String field, final T val) {
		this.hsetEx(name,field,val,null);
	}

	public <T> boolean hsetEx(final String name,final String field, final T val,final Long ttl) {
		return (boolean) redisTemplate.execute(new RedisCallback<Boolean>() {
			public Boolean doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> keySerializer = redisTemplate.getKeySerializer();
				RedisSerializer<T> valueSerializer = redisTemplate.getValueSerializer();
				byte[] name_ = keySerializer.serialize(name);
				byte[] field_ = keySerializer.serialize(field);
				byte[] value_ = valueSerializer.serialize(val);
				if(!keySet.contains(name)){
					boolean flag = connection.expire(name_,ttl==null?7*24*3600L:ttl);
					if(flag){
						keySet.add(name);
					}
				}
				return connection.hSet(name_,field_,value_);
			}
		});
	}
	/**
	 * Created by shenhj on 2017/4/22.
	 * return items(Long)
	 */
	public <T> Long hdel(final String name, final String field) {
		return (Long) redisTemplate.execute(new RedisCallback<Long>() {
			public Long doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> keySerializer = redisTemplate.getKeySerializer();
				byte[] name_ = keySerializer.serialize(name);
				byte[] field_ = keySerializer.serialize(field);
				return connection.hDel(name_,field_);
			}
		});
	}

	public <T> Long hlength(final String name) {
		return (Long) redisTemplate.execute(new RedisCallback<Long>() {
			public Long doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> keySerializer = redisTemplate.getKeySerializer();
				byte[] name_ = keySerializer.serialize(name);
				return connection.hLen(name_);
			}
		});
	}

	public <T> boolean hexit(final String name, final String field) {
		return (boolean) redisTemplate.execute(new RedisCallback<Boolean>() {
			public Boolean doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> keySerializer = redisTemplate.getKeySerializer();
				byte[] name_ = keySerializer.serialize(name);
				byte[] field_ = keySerializer.serialize(field);
				return connection.hExists(name_,field_);
			}
		});
	}


	public <T> T get(final String key) {
		return (T) redisTemplate.execute(new RedisCallback<T>() {
			public T doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> keySerializer = redisTemplate.getKeySerializer();
				RedisSerializer<T> valueSerializer = redisTemplate.getValueSerializer();
				byte[] key_ = keySerializer.serialize(key);
				byte[] value_ = connection.get(key_);
				return (T) valueSerializer.deserialize(value_);
			}
		});
	}
	/**
	 * Created by shenhj on 2017/4/22.
	 * EX   seconds  -- Set the specified expire time, in seconds.
	 * ttl default one week
	 */
	public <T> void set(final String key, final T val) {
		redisTemplate.execute(new RedisCallback<Void>() {
			public Void doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> keySerializer = redisTemplate.getKeySerializer();
				RedisSerializer<T> valueSerializer = redisTemplate.getValueSerializer();
				byte[] key_ = keySerializer.serialize(key);
				byte[] value_ = valueSerializer.serialize(val);
				connection.setEx(key_, 7*24*3600L,value_);
				return null;
			}
		});
	}

	public boolean exists(final String key) {
		return (boolean) redisTemplate.execute(new RedisCallback<Boolean>() {
			public Boolean doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> keySerializer = redisTemplate.getKeySerializer();
				byte[] key_ = keySerializer.serialize(key);
				return connection.exists(key_);
			}
		});
	}

	public Long del(final String key) {
		return (Long) redisTemplate.execute(new RedisCallback<Long>() {
			public Long doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> keySerializer = redisTemplate.getKeySerializer();
				byte[] key_ = keySerializer.serialize(key);
				return connection.del(key_);
			}
		});
	}

	/**
	 * Created by shenhj on 2017/4/22.
	 * EX   seconds  -- Set the specified expire time, in seconds.
	 * PX   milliseconds  -- Set the specified expire time, in milliseconds.
	 * NX  -- Only set the key if it does not already exist
	 * XX  -- Only set the key if it already exist.
	 */
	public <T> boolean setNX(final String key, final T val) {
		return (boolean) redisTemplate.execute(new RedisCallback<Boolean>() {
			public Boolean doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> keySerializer = redisTemplate.getKeySerializer();
				RedisSerializer<T> valueSerializer = redisTemplate.getValueSerializer();
				byte[] key_ = keySerializer.serialize(key);
				byte[] value_ = valueSerializer.serialize(val);
				return connection.setNX(key_, value_);
			}
		});
	}


	/**
	 * Created by shenhj on 2017/4/22.
	 * Set the specified expire time, in seconds.
	 * timeout  /s
	 */
	public <T> void setEX(final String key, final Long timeout, final T val) {
		redisTemplate.execute(new RedisCallback<Void>() {
			public Void doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> keySerializer = redisTemplate.getKeySerializer();
				RedisSerializer<T> valueSerializer = redisTemplate.getValueSerializer();
				byte[] key_ = keySerializer.serialize(key);
				byte[] value_ = valueSerializer.serialize(val);
				connection.setEx(key_, timeout, value_);
				return null;
			}
		});
	}

	/**
	 * 设置超时时间
	 * @param key
	 * @param timeout 超时时间 单位秒
	 * @return
	 */
	public boolean setex(final String key,final long timeout) {
		return (boolean) redisTemplate.execute(new RedisCallback<Boolean>() {
			public Boolean doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> keySerializer = redisTemplate.getKeySerializer();
				byte[] key_ = keySerializer.serialize(key);
				return connection.expire(key_,timeout);
			}
		});
	}


}