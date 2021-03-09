/***** Lobxxx Translate Finished ******/
/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package javax.management.loading;

// Java import
import com.sun.jmx.defaults.JmxProperties;

import com.sun.jmx.defaults.ServiceName;

import com.sun.jmx.remote.util.EnvHelp;

import java.io.Externalizable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandlerFactory;
import java.nio.file.Files;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import static com.sun.jmx.defaults.JmxProperties.MLET_LIB_DIR;
import static com.sun.jmx.defaults.JmxProperties.MLET_LOGGER;
import com.sun.jmx.defaults.ServiceName;
import javax.management.ServiceNotFoundException;

/**
 * Allows you to instantiate and register one or several MBeans in the MBean server
 * coming from a remote URL. M-let is a shortcut for management applet. The m-let service does this
 * by loading an m-let text file, which specifies information on the MBeans to be obtained.
 * The information on each MBean is specified in a single instance of a tag, called the MLET tag.
 * The location of the m-let text file is specified by a URL.
 * <p>
 * The <CODE>MLET</CODE> tag has the following syntax:
 * <p>
 * &lt;<CODE>MLET</CODE><BR>
 *      <CODE>CODE = </CODE><VAR>class</VAR><CODE> | OBJECT = </CODE><VAR>serfile</VAR><BR>
 *      <CODE>ARCHIVE = &quot;</CODE><VAR>archiveList</VAR><CODE>&quot;</CODE><BR>
 *      <CODE>[CODEBASE = </CODE><VAR>codebaseURL</VAR><CODE>]</CODE><BR>
 *      <CODE>[NAME = </CODE><VAR>mbeanname</VAR><CODE>]</CODE><BR>
 *      <CODE>[VERSION = </CODE><VAR>version</VAR><CODE>]</CODE><BR>
 * &gt;<BR>
 *      <CODE>[</CODE><VAR>arglist</VAR><CODE>]</CODE><BR>
 * &lt;<CODE>/MLET</CODE>&gt;
 * <p>
 * where:
 * <DL>
 * <DT><CODE>CODE = </CODE><VAR>class</VAR></DT>
 * <DD>
 * This attribute specifies the full Java class name, including package name, of the MBean to be obtained.
 * The compiled <CODE>.class</CODE> file of the MBean must be contained in one of the <CODE>.jar</CODE> files specified by the <CODE>ARCHIVE</CODE>
 * attribute. Either <CODE>CODE</CODE> or <CODE>OBJECT</CODE> must be present.
 * </DD>
 * <DT><CODE>OBJECT = </CODE><VAR>serfile</VAR></DT>
 * <DD>
 * This attribute specifies the <CODE>.ser</CODE> file that contains a serialized representation of the MBean to be obtained.
 * This file must be contained in one of the <CODE>.jar</CODE> files specified by the <CODE>ARCHIVE</CODE> attribute. If the <CODE>.jar</CODE> file contains a directory hierarchy, specify the path of the file within this hierarchy. Otherwise  a match will not be found. Either <CODE>CODE</CODE> or <CODE>OBJECT</CODE> must be present.
 * </DD>
 * <DT><CODE>ARCHIVE = &quot;</CODE><VAR>archiveList</VAR><CODE>&quot;</CODE></DT>
 * <DD>
 * This mandatory attribute specifies one or more <CODE>.jar</CODE> files
 * containing MBeans or other resources used by
 * the MBean to be obtained. One of the <CODE>.jar</CODE> files must contain the file specified by the <CODE>CODE</CODE> or <CODE>OBJECT</CODE> attribute.
 * If archivelist contains more than one file:
 * <UL>
 * <LI>Each file must be separated from the one that follows it by a comma (,).
 * <LI><VAR>archivelist</VAR> must be enclosed in double quote marks.
 * </UL>
 * All <CODE>.jar</CODE> files in <VAR>archivelist</VAR> must be stored in the directory specified by the code base URL.
 * </DD>
 * <DT><CODE>CODEBASE = </CODE><VAR>codebaseURL</VAR></DT>
 * <DD>
 * This optional attribute specifies the code base URL of the MBean to be obtained. It identifies the directory that contains
 * the <CODE>.jar</CODE> files specified by the <CODE>ARCHIVE</CODE> attribute. Specify this attribute only if the <CODE>.jar</CODE> files are not in the same
 * directory as the m-let text file. If this attribute is not specified, the base URL of the m-let text file is used.
 * </DD>
 * <DT><CODE>NAME = </CODE><VAR>mbeanname</VAR></DT>
 * <DD>
 * This optional attribute specifies the object name to be assigned to the
 * MBean instance when the m-let service registers it. If
 * <VAR>mbeanname</VAR> starts with the colon character (:), the domain
 * part of the object name is the default domain of the MBean server,
 * as returned by {@link javax.management.MBeanServer#getDefaultDomain()}.
 * </DD>
 * <DT><CODE>VERSION = </CODE><VAR>version</VAR></DT>
 * <DD>
 * This optional attribute specifies the version number of the MBean and
 * associated <CODE>.jar</CODE> files to be obtained. This version number can
 * be used to specify that the <CODE>.jar</CODE> files are loaded from the
 * server to update those stored locally in the cache the next time the m-let
 * text file is loaded. <VAR>version</VAR> must be a series of non-negative
 * decimal integers each separated by a period from the one that precedes it.
 * </DD>
 * <DT><VAR>arglist</VAR></DT>
 * <DD>
 * This optional attribute specifies a list of one or more parameters for the
 * MBean to be instantiated. This list describes the parameters to be passed the MBean's constructor.
 * Use the following syntax to specify each item in
 * <VAR>arglist</VAR>:
 * <DL>
 * <DT>&lt;<CODE>ARG TYPE=</CODE><VAR>argumentType</VAR> <CODE>VALUE=</CODE><VAR>value</VAR>&gt;</DT>
 * <DD>where:
 * <UL>
 * <LI><VAR>argumentType</VAR> is the type of the argument that will be passed as parameter to the MBean's constructor.</UL>
 * </DD>
 * </DL>
 * <P>The arguments' type in the argument list should be a Java primitive type or a Java basic type
 * (<CODE>java.lang.Boolean, java.lang.Byte, java.lang.Short, java.lang.Long, java.lang.Integer, java.lang.Float, java.lang.Double, java.lang.String</CODE>).
 * </DD>
 * </DL>
 *
 * When an m-let text file is loaded, an
 * instance of each MBean specified in the file is created and registered.
 * <P>
 * The m-let service extends the <CODE>java.net.URLClassLoader</CODE> and can be used to load remote classes
 * and jar files in the VM of the agent.
 * <p><STRONG>Note - </STRONG> The <CODE>MLet</CODE> class loader uses the {@link javax.management.MBeanServerFactory#getClassLoaderRepository(javax.management.MBeanServer)}
 * to load classes that could not be found in the loaded jar files.
 *
 * <p>
 *  允许您在来自远程URL的MBean服务器中实例化和注册一个或多个MBean M-let是管理applet的快捷方式m-let服务通过加载m-let文本文件来执行此操作,该文本文件指定了MBean的信息
 * 要获得关于每个MBean的信息在标签的单个实例中被指定,称为MLET标签.m-let文本文件的位置由URL指定。
 * <p>
 *  <CODE> MLET </CODE>标记具有以下语法：
 * <p>
 * &lt; CODE&gt; MLET </CODE> <BR> <CODE> CODE = </CODE> <VAR> class </VAR> <CODE> | OBJECT = </CODE> <VAR>
 *  serfile </VAR> <BR> <CODE> ARCHIVE ="</CODE> <VAR> archiveList </VAR> <CODE>"</CODE> <BR> <CODE > [C
 * ODEBASE = </CODE> <VAR> codebaseURL </VAR> <CODE>] </CODE> <BR> <CODE> / CODE> <BR> <CODE> [VERSION =
 *  </CODE> <VAR> version </VAR> <CODE>] </CODE> <BR>&gt; <BR> <CODE> [</CODE> <VAR > arglist </VAR> <CODE>
 * ] </CODE> <BR>&lt; <CODE> / MLET </CODE>。
 * <p>
 *  哪里：
 * <DL>
 *  <DT> <CODE> CODE = </CODE> <VAR> class </VAR> </DT>
 * <DD>
 * 此属性指定要获取的MBean的完整Java类名称(包括包名称)MBean的编译的<CODE>类</CODE>文件必须包含在指定的<CODE> jar </CODE>文件之一中通过<CODE> ARCHI
 * VE </CODE>属性必须存在<CODE> CODE </CODE>或<CODE> OBJECT </CODE>。
 * </DD>
 *  <DT> <CODE> OBJECT = </CODE> <VAR> serfile </VAR> </DT>
 * <DD>
 * 此属性指定包含要获取的MBean的序列化表示的<CODE> ser </CODE>文件此文件必须包含在由<CODE> ARCHIVE </CODE>指定的<CODE> / CODE>属性如果<CODE>
 *  jar </CODE>文件包含目录层次结构,请在此层次结构中指定文件的路径。
 * 否则将不会找到匹配<CODE> CODE </CODE>或<CODE> OBJECT </CODE>必须存在。
 * </DD>
 *  <DT> <CODE> ARCHIVE ="</CODE> <VAR> archiveList </VAR> <CODE>"</CODE> </DT>
 * <DD>
 * 此强制属性指定一个或多个包含要获取的MBean或其他资源的<CODE> jar </CODE>文件<CODE> jar </CODE>文件必须包含由<CODE> CODE </CODE>或<CODE> 
 * OBJECT </CODE>属性如果archivelist包含多个文件：。
 * <UL>
 *  <LI>每个文件必须与逗号(,)之后的文件隔开。<LI> <VAR> archivelist </VAR>必须用双引号括起来
 * </UL>
 *  <VAR> archivelist </VAR>中的所有<CODE> jar </CODE>文件必须存储在由代码库URL指定的目录中
 * </DD>
 *  <DT> <CODE> CODEBASE = </CODE> <VAR> codebaseURL </VAR> </DT>
 * <DD>
 * 此可选属性指定要获取的MBean的代码库URL它标识包含由<CODE> ARCHIVE </CODE>属性指定的<CODE> jar </CODE>文件的目录只有当<CODE> > jar </CODE>
 * 文件与m-let文本文件不在同一目录中如果未指定此属性,则使用m-let文本文件的基本URL。
 * </DD>
 *  <DT> <CODE> NAME = </CODE> <VAR> mbeanname </VAR> </DT>
 * <DD>
 *  此可选属性指定在m-let服务注册时要分配给MBean实例的对象名称如果<VAR> mbeanname </VAR>以冒号字符(:)开头,则对象名称的域部分为默认值域的MBean服务器,由{@link javaxmanagementMBeanServer#getDefaultDomain()}
 * 返回。
 * </DD>
 * <DT> <CODE> VERSION = </CODE> <VAR>版本</VAR> </DT>
 * <DD>
 *  此可选属性指定要获取的MBean的版本号和相关的<CODE> jar </CODE>文件此版本号可用于指定从服务器加载<CODE> jar </CODE>文件以更新下一次加载m-let文本文件时本地存
 * 储在缓存中的内容<VAR> version </VAR>必须是一系列非负的十进制整数,每个都以一个句点分隔,。
 * </DD>
 *  <DT> <VAR> arglist </VAR> </DT>
 * <DD>
 *  此可选属性指定要实例化的MBean的一个或多个参数的列表此列表描述要传递MBean的构造函数的参数使用以下语法指定<VAR> arglist </VAR>中的每个项目：
 * <DL>
 * <DT> <CODE> ARG TYPE = </CODE> <VAR> argumentType </VAR> <CODE> VALUE = </CODE> <VAR> value </VAR>&gt
 * ; </DT> <DD> ：。
 * <UL>
 *  <LI> <VAR> argumentType </VAR>是将作为参数传递给MBean构造函数的参数的类型</UL>
 * </DD>
 * </DL>
 *  <P>参数列表中的参数类型应该是Java基本类型或Java基本类型(<CODE> javalangBoolean,javalangByte,javalangShort,javalangLong,jav
 * alangInteger,javalangFloat,javalangDouble,javalangString </CODE>。
 * </DD>
 * </DL>
 * 
 *  当加载m-let文本文件时,将创建并注册文件中指定的每个MBean的实例
 * <P>
 * m-let服务扩展<CODE> javanetURLClassLoader </CODE>,并可用于在代理程序的VM中加载远程类和jar文件<p> <STRONG>注 -  </STRONG> <CODE>
 *  / CODE>类装载器使用{@link javaxmanagementMBeanServerFactory#getClassLoaderRepository(javaxmanagementMBeanServer)}
 * 加载在加载的jar文件中找不到的类。
 * 
 * 
 * @since 1.5
 */
public class MLet extends java.net.URLClassLoader
     implements MLetMBean, MBeanRegistration, Externalizable {

     private static final long serialVersionUID = 3636148327800330130L;

     /*
     * ------------------------------------------
     *   PRIVATE VARIABLES
     * ------------------------------------------
     * <p>
     *  ------------------------------------------私人变数------ ------------------------------------
     * 
     */

     /**
      * The reference to the MBean server.
      * <p>
      *  对MBean服务器的引用
      * 
      * 
      * @serial
      */
     private MBeanServer server = null;


     /**
      * The list of instances of the <CODE>MLetContent</CODE>
      * class found at the specified URL.
      * <p>
      *  在指定的URL找到的<CODE> MLetContent </CODE>类的实例列表
      * 
      * 
      * @serial
      */
     private List<MLetContent> mletList = new ArrayList<MLetContent>();


     /**
      * The directory used for storing libraries locally before they are loaded.
      * <p>
      *  用于在加载库之前本地存储库的目录
      * 
      */
     private String libraryDirectory;


     /**
      * The object name of the MLet Service.
      * <p>
      *  MLet服务的对象名称
      * 
      * 
      * @serial
      */
     private ObjectName mletObjectName = null;

     /**
      * The URLs of the MLet Service.
      * <p>
      *  MLet服务的URL
      * 
      * 
      * @serial
      */
     private URL[] myUrls = null;

     /**
      * What ClassLoaderRepository, if any, to use if this MLet
      * doesn't find an asked-for class.
      * <p>
      * 如果此MLet没有找到一个询问类,则使用什么ClassLoaderRepository(如果有)
      * 
      */
     private transient ClassLoaderRepository currentClr;

     /**
      * True if we should consult the {@link ClassLoaderRepository}
      * when we do not find a class ourselves.
      * <p>
      *  是的,如果我们应该查阅{@link ClassLoaderRepository}当我们没有找到一个类
      * 
      */
     private transient boolean delegateToCLR;

     /**
      * objects maps from primitive classes to primitive object classes.
      * <p>
      *  对象映射从原始类到原始对象类
      * 
      */
     private Map<String,Class<?>> primitiveClasses =
         new HashMap<String,Class<?>>(8) ;
     {
         primitiveClasses.put(Boolean.TYPE.toString(), Boolean.class);
         primitiveClasses.put(Character.TYPE.toString(), Character.class);
         primitiveClasses.put(Byte.TYPE.toString(), Byte.class);
         primitiveClasses.put(Short.TYPE.toString(), Short.class);
         primitiveClasses.put(Integer.TYPE.toString(), Integer.class);
         primitiveClasses.put(Long.TYPE.toString(), Long.class);
         primitiveClasses.put(Float.TYPE.toString(), Float.class);
         primitiveClasses.put(Double.TYPE.toString(), Double.class);

     }


     /*
      * ------------------------------------------
      *  CONSTRUCTORS
      * ------------------------------------------
      * <p>
      *  ------------------------------------------建筑师------- -----------------------------------
      * 
      */

     /*
      * The constructor stuff would be considerably simplified if our
      * parent, URLClassLoader, specified that its one- and
      * two-argument constructors were equivalent to its
      * three-argument constructor with trailing null arguments.  But
      * it doesn't, which prevents us from having all the constructors
      * but one call this(...args...).
      * <p>
      *  如果我们的父URLClassLoader指定它的一个和两个参数的构造函数等价于它的三参数构造函数和尾随空参数,那么构造函数的东西会大大简化。
      * 但是它不会,这会阻止我们拥有所有的构造函数,一个调用this(args)。
      * 
      */

     /**
      * Constructs a new MLet using the default delegation parent ClassLoader.
      * <p>
      *  使用默认委托父类ClassLoader构造新的MLet
      * 
      */
     public MLet() {
         this(new URL[0]);
     }

     /**
      * Constructs a new MLet for the specified URLs using the default
      * delegation parent ClassLoader.  The URLs will be searched in
      * the order specified for classes and resources after first
      * searching in the parent class loader.
      *
      * <p>
      * 使用默认委托父目录为指定的URL构造一个新的MLet ClassLoader在首次在父类加载器中搜索之后,将按照为类和资源指定的顺序搜索URL
      * 
      * 
      * @param  urls  The URLs from which to load classes and resources.
      *
      */
     public MLet(URL[] urls) {
         this(urls, true);
     }

     /**
      * Constructs a new MLet for the given URLs. The URLs will be
      * searched in the order specified for classes and resources
      * after first searching in the specified parent class loader.
      * The parent argument will be used as the parent class loader
      * for delegation.
      *
      * <p>
      *  为给定的URL构造一个新的MLet在首次在指定的父类加载器中搜索之后,将按照为类和资源指定的顺序搜索URL。parent参数将被用作父类加载器以进行委派
      * 
      * 
      * @param  urls  The URLs from which to load classes and resources.
      * @param  parent The parent class loader for delegation.
      *
      */
     public MLet(URL[] urls, ClassLoader parent) {
         this(urls, parent, true);
     }

     /**
      * Constructs a new MLet for the specified URLs, parent class
      * loader, and URLStreamHandlerFactory. The parent argument will
      * be used as the parent class loader for delegation. The factory
      * argument will be used as the stream handler factory to obtain
      * protocol handlers when creating new URLs.
      *
      * <p>
      *  为指定的URL,父类加载器和URLStreamHandlerFactory构造一个新的MLet父参数将用作父类加载器以进行委派工厂参数将用作流处理程序工厂以在创建新URL时获取协议处理程序
      * 
      * 
      * @param  urls  The URLs from which to load classes and resources.
      * @param  parent The parent class loader for delegation.
      * @param  factory  The URLStreamHandlerFactory to use when creating URLs.
      *
      */
     public MLet(URL[] urls,
                 ClassLoader parent,
                 URLStreamHandlerFactory factory) {
         this(urls, parent, factory, true);
     }

     /**
      * Constructs a new MLet for the specified URLs using the default
      * delegation parent ClassLoader.  The URLs will be searched in
      * the order specified for classes and resources after first
      * searching in the parent class loader.
      *
      * <p>
      * 使用默认委托父目录为指定的URL构造一个新的MLet ClassLoader在首次在父类加载器中搜索之后,将按照为类和资源指定的顺序搜索URL
      * 
      * 
      * @param  urls  The URLs from which to load classes and resources.
      * @param  delegateToCLR  True if, when a class is not found in
      * either the parent ClassLoader or the URLs, the MLet should delegate
      * to its containing MBeanServer's {@link ClassLoaderRepository}.
      *
      */
     public MLet(URL[] urls, boolean delegateToCLR) {
         super(urls);
         init(delegateToCLR);
     }

     /**
      * Constructs a new MLet for the given URLs. The URLs will be
      * searched in the order specified for classes and resources
      * after first searching in the specified parent class loader.
      * The parent argument will be used as the parent class loader
      * for delegation.
      *
      * <p>
      *  为给定的URL构造一个新的MLet在首次在指定的父类加载器中搜索之后,将按照为类和资源指定的顺序搜索URL。parent参数将被用作父类加载器以进行委派
      * 
      * 
      * @param  urls  The URLs from which to load classes and resources.
      * @param  parent The parent class loader for delegation.
      * @param  delegateToCLR  True if, when a class is not found in
      * either the parent ClassLoader or the URLs, the MLet should delegate
      * to its containing MBeanServer's {@link ClassLoaderRepository}.
      *
      */
     public MLet(URL[] urls, ClassLoader parent, boolean delegateToCLR) {
         super(urls, parent);
         init(delegateToCLR);
     }

     /**
      * Constructs a new MLet for the specified URLs, parent class
      * loader, and URLStreamHandlerFactory. The parent argument will
      * be used as the parent class loader for delegation. The factory
      * argument will be used as the stream handler factory to obtain
      * protocol handlers when creating new URLs.
      *
      * <p>
      *  为指定的URL,父类加载器和URLStreamHandlerFactory构造一个新的MLet父参数将用作父类加载器以进行委派工厂参数将用作流处理程序工厂以在创建新URL时获取协议处理程序
      * 
      * 
      * @param  urls  The URLs from which to load classes and resources.
      * @param  parent The parent class loader for delegation.
      * @param  factory  The URLStreamHandlerFactory to use when creating URLs.
      * @param  delegateToCLR  True if, when a class is not found in
      * either the parent ClassLoader or the URLs, the MLet should delegate
      * to its containing MBeanServer's {@link ClassLoaderRepository}.
      *
      */
     public MLet(URL[] urls,
                 ClassLoader parent,
                 URLStreamHandlerFactory factory,
                 boolean delegateToCLR) {
         super(urls, parent, factory);
         init(delegateToCLR);
     }

     private void init(boolean delegateToCLR) {
         this.delegateToCLR = delegateToCLR;

         try {
             libraryDirectory = System.getProperty(MLET_LIB_DIR);
             if (libraryDirectory == null)
                 libraryDirectory = getTmpDir();
         } catch (SecurityException e) {
             // OK : We don't do AccessController.doPrivileged, but we don't
             //      stop the user from creating an MLet just because they
             //      can't read the MLET_LIB_DIR or java.io.tmpdir properties
             //      either.
         }
     }


     /*
      * ------------------------------------------
      *  PUBLIC METHODS
      * ------------------------------------------
      * <p>
      * ------------------------------------------公共方法------------------------------------
      * 
      */


     /**
      * Appends the specified URL to the list of URLs to search for classes and
      * resources.
      * <p>
      *  将指定的URL附加到URL列表以搜索类和资源
      * 
      */
     public void addURL(URL url) {
         if (!Arrays.asList(getURLs()).contains(url))
             super.addURL(url);
     }

     /**
      * Appends the specified URL to the list of URLs to search for classes and
      * resources.
      * <p>
      *  将指定的URL附加到URL列表以搜索类和资源
      * 
      * 
      * @exception ServiceNotFoundException The specified URL is malformed.
      */
     public void addURL(String url) throws ServiceNotFoundException {
         try {
             URL ur = new URL(url);
             if (!Arrays.asList(getURLs()).contains(ur))
                 super.addURL(ur);
         } catch (MalformedURLException e) {
             if (MLET_LOGGER.isLoggable(Level.FINEST)) {
                 MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                         "addUrl", "Malformed URL: " + url, e);
             }
             throw new
                 ServiceNotFoundException("The specified URL is malformed");
         }
     }

     /** Returns the search path of URLs for loading classes and resources.
      * This includes the original list of URLs specified to the constructor,
      * along with any URLs subsequently appended by the addURL() method.
      * <p>
      *  这包括指定给构造函数的原始URL列表,以及随后由addURL()方法附加的任何URL
      * 
      */
     public URL[] getURLs() {
         return super.getURLs();
     }

     /**
      * Loads a text file containing MLET tags that define the MBeans to
      * be added to the MBean server. The location of the text file is specified by
      * a URL. The MBeans specified in the MLET file will be instantiated and
      * registered in the MBean server.
      *
      * <p>
      *  加载包含定义要添加到MBean服务器的MBean的MLET标记的文本文件文本文件的位置由URL指定在MLET文件中指定的MBeans将被实例化并在MBean服务器中注册
      * 
      * 
      * @param url The URL of the text file to be loaded as URL object.
      *
      * @return  A set containing one entry per MLET tag in the m-let text file loaded.
      * Each entry specifies either the ObjectInstance for the created MBean, or a throwable object
      * (that is, an error or an exception) if the MBean could not be created.
      *
      * @exception ServiceNotFoundException One of the following errors has occurred: The m-let text file does
      * not contain an MLET tag, the m-let text file is not found, a mandatory
      * attribute of the MLET tag is not specified, the value of url is
      * null.
      * @exception IllegalStateException MLet MBean is not registered with an MBeanServer.
      */
     public Set<Object> getMBeansFromURL(URL url)
             throws ServiceNotFoundException  {
         if (url == null) {
             throw new ServiceNotFoundException("The specified URL is null");
         }
         return getMBeansFromURL(url.toString());
     }

     /**
      * Loads a text file containing MLET tags that define the MBeans to
      * be added to the MBean server. The location of the text file is specified by
      * a URL. The MBeans specified in the MLET file will be instantiated and
      * registered in the MBean server.
      *
      * <p>
      * 加载包含定义要添加到MBean服务器的MBean的MLET标记的文本文件文本文件的位置由URL指定在MLET文件中指定的MBeans将被实例化并在MBean服务器中注册
      * 
      * 
      * @param url The URL of the text file to be loaded as String object.
      *
      * @return A set containing one entry per MLET tag in the m-let
      * text file loaded.  Each entry specifies either the
      * ObjectInstance for the created MBean, or a throwable object
      * (that is, an error or an exception) if the MBean could not be
      * created.
      *
      * @exception ServiceNotFoundException One of the following
      * errors has occurred: The m-let text file does not contain an
      * MLET tag, the m-let text file is not found, a mandatory
      * attribute of the MLET tag is not specified, the url is
      * malformed.
      * @exception IllegalStateException MLet MBean is not registered
      * with an MBeanServer.
      *
      */
     public Set<Object> getMBeansFromURL(String url)
             throws ServiceNotFoundException  {

         String mth = "getMBeansFromURL";

         if (server == null) {
             throw new IllegalStateException("This MLet MBean is not " +
                                             "registered with an MBeanServer.");
         }
         // Parse arguments
         if (url == null) {
             MLET_LOGGER.logp(Level.FINER, MLet.class.getName(),
                     mth, "URL is null");
             throw new ServiceNotFoundException("The specified URL is null");
         } else {
             url = url.replace(File.separatorChar,'/');
         }
         if (MLET_LOGGER.isLoggable(Level.FINER)) {
             MLET_LOGGER.logp(Level.FINER, MLet.class.getName(),
                     mth, "<URL = " + url + ">");
         }

         // Parse URL
         try {
             MLetParser parser = new MLetParser();
             mletList = parser.parseURL(url);
         } catch (Exception e) {
             final String msg =
                 "Problems while parsing URL [" + url +
                 "], got exception [" + e.toString() + "]";
             MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth, msg);
             throw EnvHelp.initCause(new ServiceNotFoundException(msg), e);
         }

         // Check that the list of MLets is not empty
         if (mletList.size() == 0) {
             final String msg =
                 "File " + url + " not found or MLET tag not defined in file";
             MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth, msg);
             throw new ServiceNotFoundException(msg);
         }

         // Walk through the list of MLets
         Set<Object> mbeans = new HashSet<Object>();
         for (MLetContent elmt : mletList) {
             // Initialize local variables
             String code = elmt.getCode();
             if (code != null) {
                 if (code.endsWith(".class")) {
                     code = code.substring(0, code.length() - 6);
                 }
             }
             String name = elmt.getName();
             URL codebase = elmt.getCodeBase();
             String version = elmt.getVersion();
             String serName = elmt.getSerializedObject();
             String jarFiles = elmt.getJarFiles();
             URL documentBase = elmt.getDocumentBase();

             // Display debug information
             if (MLET_LOGGER.isLoggable(Level.FINER)) {
                 final StringBuilder strb = new StringBuilder()
                 .append("\n\tMLET TAG     = ").append(elmt.getAttributes())
                 .append("\n\tCODEBASE     = ").append(codebase)
                 .append("\n\tARCHIVE      = ").append(jarFiles)
                 .append("\n\tCODE         = ").append(code)
                 .append("\n\tOBJECT       = ").append(serName)
                 .append("\n\tNAME         = ").append(name)
                 .append("\n\tVERSION      = ").append(version)
                 .append("\n\tDOCUMENT URL = ").append(documentBase);
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(),
                         mth, strb.toString());
             }

             // Load classes from JAR files
             StringTokenizer st = new StringTokenizer(jarFiles, ",", false);
             while (st.hasMoreTokens()) {
                 String tok = st.nextToken().trim();
                 if (MLET_LOGGER.isLoggable(Level.FINER)) {
                     MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                             "Load archive for codebase <" + codebase +
                             ">, file <" + tok + ">");
                 }
                 // Check which is the codebase to be used for loading the jar file.
                 // If we are using the base MLet implementation then it will be
                 // always the remote server but if the service has been extended in
                 // order to support caching and versioning then this method will
                 // return the appropriate one.
                 //
                 try {
                     codebase = check(version, codebase, tok, elmt);
                 } catch (Exception ex) {
                     MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                             mth, "Got unexpected exception", ex);
                     mbeans.add(ex);
                     continue;
                 }

                 // Appends the specified JAR file URL to the list of
                 // URLs to search for classes and resources.
                 try {
                     if (!Arrays.asList(getURLs())
                         .contains(new URL(codebase.toString() + tok))) {
                         addURL(codebase + tok);
                     }
                 } catch (MalformedURLException me) {
                     // OK : Ignore jar file if its name provokes the
                     // URL to be an invalid one.
                 }

             }
             // Instantiate the class specified in the
             // CODE or OBJECT section of the MLet tag
             //
             Object o;
             ObjectInstance objInst;

             if (code != null && serName != null) {
                 final String msg =
                     "CODE and OBJECT parameters cannot be specified at the " +
                     "same time in tag MLET";
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth, msg);
                 mbeans.add(new Error(msg));
                 continue;
             }
             if (code == null && serName == null) {
                 final String msg =
                     "Either CODE or OBJECT parameter must be specified in " +
                     "tag MLET";
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth, msg);
                 mbeans.add(new Error(msg));
                 continue;
             }
             try {
                 if (code != null) {

                     List<String> signat = elmt.getParameterTypes();
                     List<String> stringPars = elmt.getParameterValues();
                     List<Object> objectPars = new ArrayList<Object>();

                     for (int i = 0; i < signat.size(); i++) {
                         objectPars.add(constructParameter(stringPars.get(i),
                                                           signat.get(i)));
                     }
                     if (signat.isEmpty()) {
                         if (name == null) {
                             objInst = server.createMBean(code, null,
                                                          mletObjectName);
                         } else {
                             objInst = server.createMBean(code,
                                                          new ObjectName(name),
                                                          mletObjectName);
                         }
                     } else {
                         Object[] parms = objectPars.toArray();
                         String[] signature = new String[signat.size()];
                         signat.toArray(signature);
                         if (MLET_LOGGER.isLoggable(Level.FINEST)) {
                             final StringBuilder strb = new StringBuilder();
                             for (int i = 0; i < signature.length; i++) {
                                 strb.append("\n\tSignature     = ")
                                 .append(signature[i])
                                 .append("\t\nParams        = ")
                                 .append(parms[i]);
                             }
                             MLET_LOGGER.logp(Level.FINEST,
                                     MLet.class.getName(),
                                     mth, strb.toString());
                         }
                         if (name == null) {
                             objInst =
                                 server.createMBean(code, null, mletObjectName,
                                                    parms, signature);
                         } else {
                             objInst =
                                 server.createMBean(code, new ObjectName(name),
                                                    mletObjectName, parms,
                                                    signature);
                         }
                     }
                 } else {
                     o = loadSerializedObject(codebase,serName);
                     if (name == null) {
                         server.registerMBean(o, null);
                     } else {
                         server.registerMBean(o,  new ObjectName(name));
                     }
                     objInst = new ObjectInstance(name, o.getClass().getName());
                 }
             } catch (ReflectionException  ex) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                         "ReflectionException", ex);
                 mbeans.add(ex);
                 continue;
             } catch (InstanceAlreadyExistsException  ex) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                         "InstanceAlreadyExistsException", ex);
                 mbeans.add(ex);
                 continue;
             } catch (MBeanRegistrationException ex) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                         "MBeanRegistrationException", ex);
                 mbeans.add(ex);
                 continue;
             } catch (MBeanException  ex) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                         "MBeanException", ex);
                 mbeans.add(ex);
                 continue;
             } catch (NotCompliantMBeanException  ex) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                         "NotCompliantMBeanException", ex);
                 mbeans.add(ex);
                 continue;
             } catch (InstanceNotFoundException   ex) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                         "InstanceNotFoundException", ex);
                 mbeans.add(ex);
                 continue;
             } catch (IOException ex) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                         "IOException", ex);
                 mbeans.add(ex);
                 continue;
             } catch (SecurityException ex) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                         "SecurityException", ex);
                 mbeans.add(ex);
                 continue;
             } catch (Exception ex) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                         "Exception", ex);
                 mbeans.add(ex);
                 continue;
             } catch (Error ex) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                         "Error", ex);
                 mbeans.add(ex);
                 continue;
             }
             mbeans.add(objInst);
         }
         return mbeans;
     }

     /**
      * Gets the current directory used by the library loader for
      * storing native libraries before they are loaded into memory.
      *
      * <p>
      *  获取库加载器用于在将本机库加载到内存之前存储本机库所使用的当前目录
      * 
      * 
      * @return The current directory used by the library loader.
      *
      * @see #setLibraryDirectory
      *
      * @throws UnsupportedOperationException if this implementation
      * does not support storing native libraries in this way.
      */
     public synchronized String getLibraryDirectory() {
         return libraryDirectory;
     }

     /**
      * Sets the directory used by the library loader for storing
      * native libraries before they are loaded into memory.
      *
      * <p>
      *  设置库加载器用于在将本机库加载到内存之前存储本机库的目录
      * 
      * 
      * @param libdir The directory used by the library loader.
      *
      * @see #getLibraryDirectory
      *
      * @throws UnsupportedOperationException if this implementation
      * does not support storing native libraries in this way.
      */
     public synchronized void setLibraryDirectory(String libdir) {
         libraryDirectory = libdir;
     }

     /**
      * Allows the m-let to perform any operations it needs before
      * being registered in the MBean server. If the ObjectName is
      * null, the m-let provides a default name for its registration
      * &lt;defaultDomain&gt;:type=MLet
      *
      * <p>
      *  允许m-let在注册到MBean服务器之前执行所需的任何操作如果ObjectName为null,则m-let为其注册&lt; defaultDomain&gt;提供默认名称：type = MLet
      * 
      * 
      * @param server The MBean server in which the m-let will be registered.
      * @param name The object name of the m-let.
      *
      * @return  The name of the m-let registered.
      *
      * @exception java.lang.Exception This exception should be caught by the MBean server and re-thrown
      *as an MBeanRegistrationException.
      */
     public ObjectName preRegister(MBeanServer server, ObjectName name)
             throws Exception {

         // Initialize local pointer to the MBean server
         setMBeanServer(server);

         // If no name is specified return a default name for the MLet
         if (name == null) {
             name = new ObjectName(server.getDefaultDomain() + ":" + ServiceName.MLET);
         }

        this.mletObjectName = name;
        return this.mletObjectName;
     }

     /**
      * Allows the m-let to perform any operations needed after having been
      * registered in the MBean server or after the registration has failed.
      *
      * <p>
      * 允许m-let在MBean服务器中注册后或注册失败后执行任何所需的操作
      * 
      * 
      * @param registrationDone Indicates whether or not the m-let has
      * been successfully registered in the MBean server. The value
      * false means that either the registration phase has failed.
      *
      */
     public void postRegister (Boolean registrationDone) {
     }

     /**
      * Allows the m-let to perform any operations it needs before being unregistered
      * by the MBean server.
      *
      * <p>
      *  允许m-let在MBean服务器取消注册之前执行其所需的任何操作
      * 
      * 
      * @exception java.lang.Exception This exception should be caught
      * by the MBean server and re-thrown as an
      * MBeanRegistrationException.
      */
     public void preDeregister() throws java.lang.Exception {
     }


     /**
      * Allows the m-let to perform any operations needed after having been
      * unregistered in the MBean server.
      * <p>
      *  允许m-let在MBean服务器中取消注册后执行所需的任何操作
      * 
      */
     public void postDeregister() {
     }

     /**
      * <p>Save this MLet's contents to the given {@link ObjectOutput}.
      * Not all implementations support this method.  Those that do not
      * throw {@link UnsupportedOperationException}.  A subclass may
      * override this method to support it or to change the format of
      * the written data.</p>
      *
      * <p>The format of the written data is not specified, but if
      * an implementation supports {@link #writeExternal} it must
      * also support {@link #readExternal} in such a way that what is
      * written by the former can be read by the latter.</p>
      *
      * <p>
      *  <p>将此MLet的内容保存到给定的{@link ObjectOutput}并非所有实现都支持此方法不会抛出{@link UnsupportedOperationException}的子类可以覆盖此方
      * 法以支持它或更改写入数据的格式< / p>。
      * 
      * <p>未指定写入数据的格式,但如果实现支持{@link #writeExternal},它还必须支持{@link #readExternal},这样前者写入的内容可以由后者</p>
      * 
      * 
      * @param out The object output stream to write to.
      *
      * @exception IOException If a problem occurred while writing.
      * @exception UnsupportedOperationException If this
      * implementation does not support this operation.
      */
     public void writeExternal(ObjectOutput out)
             throws IOException, UnsupportedOperationException {
         throw new UnsupportedOperationException("MLet.writeExternal");
     }

     /**
      * <p>Restore this MLet's contents from the given {@link ObjectInput}.
      * Not all implementations support this method.  Those that do not
      * throw {@link UnsupportedOperationException}.  A subclass may
      * override this method to support it or to change the format of
      * the read data.</p>
      *
      * <p>The format of the read data is not specified, but if an
      * implementation supports {@link #readExternal} it must also
      * support {@link #writeExternal} in such a way that what is
      * written by the latter can be read by the former.</p>
      *
      * <p>
      *  <p>从给定的{@link ObjectInput}恢复这个MLet的内容不是所有的实现都支持这个方法那些不抛出{@link UnsupportedOperationException}的子类可以重写
      * 这个方法来支持它,或者改变读取数据的格式< / p>。
      * 
      *  <p>未指定读取数据的格式,但如果实现支持{@link #readExternal},它还必须支持{@link #writeExternal},以便由后者写入的内容可以由前</p>
      * 
      * 
      * @param in The object input stream to read from.
      *
      * @exception IOException if a problem occurred while reading.
      * @exception ClassNotFoundException if the class for the object
      * being restored cannot be found.
      * @exception UnsupportedOperationException if this
      * implementation does not support this operation.
      */
     public void readExternal(ObjectInput in)
             throws IOException, ClassNotFoundException,
                    UnsupportedOperationException {
         throw new UnsupportedOperationException("MLet.readExternal");
     }

     /*
      * ------------------------------------------
      *  PACKAGE METHODS
      * ------------------------------------------
      * <p>
      * ------------------------------------------包装方法------ ------------------------------------
      * 
      */

     /**
      * <p>Load a class, using the given {@link ClassLoaderRepository} if
      * the class is not found in this MLet's URLs.  The given
      * ClassLoaderRepository can be null, in which case a {@link
      * ClassNotFoundException} occurs immediately if the class is not
      * found in this MLet's URLs.</p>
      *
      * <p>
      *  <p>使用给定的{@link ClassLoaderRepository}(如果在此MLet的URL中找不到类)加载类给定的ClassLoaderRepository可以为null,在这种情况下,如果
      * 在类中没有找到类,则会立即发生{@link ClassNotFoundException}这个MLet的网址</p>。
      * 
      * 
      * @param name The name of the class we want to load.
      * @param clr  The ClassLoaderRepository that will be used to search
      *             for the given class, if it is not found in this
      *             ClassLoader.  May be null.
      * @return The resulting Class object.
      * @exception ClassNotFoundException The specified class could not be
      *            found in this ClassLoader nor in the given
      *            ClassLoaderRepository.
      *
      */
     public synchronized Class<?> loadClass(String name,
                                            ClassLoaderRepository clr)
              throws ClassNotFoundException {
         final ClassLoaderRepository before=currentClr;
         try {
             currentClr = clr;
             return loadClass(name);
         } finally {
             currentClr = before;
         }
     }

     /*
      * ------------------------------------------
      *  PROTECTED METHODS
      * ------------------------------------------
      * <p>
      *  ------------------------------------------保护方法------ ------------------------------------
      * 
      */

     /**
      * This is the main method for class loaders that is being redefined.
      *
      * <p>
      *  这是正在重新定义的类加载器的主要方法
      * 
      * 
      * @param name The name of the class.
      *
      * @return The resulting Class object.
      *
      * @exception ClassNotFoundException The specified class could not be
      *            found.
      */
     protected Class<?> findClass(String name) throws ClassNotFoundException {
         /* currentClr is context sensitive - used to avoid recursion
            in the class loader repository.  (This is no longer
            necessary with the new CLR semantics but is kept for
            compatibility with code that might have called the
         /* <p>
         /*  在类加载器存储库(这不再需要与新的CLR语义,但保持与代码的兼容性,可能已经调用
         /* 
         /* 
            two-parameter loadClass explicitly.)  */
         return findClass(name, currentClr);
     }

     /**
      * Called by {@link MLet#findClass(java.lang.String)}.
      *
      * <p>
      *  由{@link MLet#findClass(javalangString)}调用
      * 
      * 
      * @param name The name of the class that we want to load/find.
      * @param clr The ClassLoaderRepository that can be used to search
      *            for the given class. This parameter is
      *            <code>null</code> when called from within the
      *            {@link javax.management.MBeanServerFactory#getClassLoaderRepository(javax.management.MBeanServer) Class Loader Repository}.
      * @exception ClassNotFoundException The specified class could not be
      *            found.
      *
      **/
     Class<?> findClass(String name, ClassLoaderRepository clr)
         throws ClassNotFoundException {
         Class<?> c = null;
         MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), "findClass", name);
         // Try looking in the JAR:
         try {
             c = super.findClass(name);
             if (MLET_LOGGER.isLoggable(Level.FINER)) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(),
                         "findClass",
                         "Class " + name + " loaded through MLet classloader");
             }
         } catch (ClassNotFoundException e) {
             // Drop through
             if (MLET_LOGGER.isLoggable(Level.FINEST)) {
                 MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                         "findClass",
                         "Class " + name + " not found locally");
             }
         }
         // if we are not called from the ClassLoaderRepository
         if (c == null && delegateToCLR && clr != null) {
             // Try the classloader repository:
             //
             try {
                 if (MLET_LOGGER.isLoggable(Level.FINEST)) {
                     MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                             "findClass",
                             "Class " + name + " : looking in CLR");
                 }
                 c = clr.loadClassBefore(this, name);
                 // The loadClassBefore method never returns null.
                 // If the class is not found we get an exception.
                 if (MLET_LOGGER.isLoggable(Level.FINER)) {
                     MLET_LOGGER.logp(Level.FINER, MLet.class.getName(),
                             "findClass",
                             "Class " + name + " loaded through " +
                             "the default classloader repository");
                 }
             } catch (ClassNotFoundException e) {
                 // Drop through
                 if (MLET_LOGGER.isLoggable(Level.FINEST)) {
                     MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                             "findClass",
                             "Class " + name + " not found in CLR");
                 }
             }
         }
         if (c == null) {
             MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                     "findClass", "Failed to load class " + name);
             throw new ClassNotFoundException(name);
         }
         return c;
     }

     /**
      * Returns the absolute path name of a native library. The VM
      * invokes this method to locate the native libraries that belong
      * to classes loaded with this class loader. Libraries are
      * searched in the JAR files using first just the native library
      * name and if not found the native library name together with
      * the architecture-specific path name
      * (<code>OSName/OSArch/OSVersion/lib/nativelibname</code>), i.e.
      * <p>
      * the library stat on Solaris SPARC 5.7 will be searched in the JAR file as:
      * <OL>
      * <LI>libstat.so
      * <LI>SunOS/sparc/5.7/lib/libstat.so
      * </OL>
      * the library stat on Windows NT 4.0 will be searched in the JAR file as:
      * <OL>
      * <LI>stat.dll
      * <LI>WindowsNT/x86/4.0/lib/stat.dll
      * </OL>
      *
      * <p>More specifically, let <em>{@code nativelibname}</em> be the result of
      * {@link System#mapLibraryName(java.lang.String)
      * System.mapLibraryName}{@code (libname)}.  Then the following names are
      * searched in the JAR files, in order:<br>
      * <em>{@code nativelibname}</em><br>
      * {@code <os.name>/<os.arch>/<os.version>/lib/}<em>{@code nativelibname}</em><br>
      * where {@code <X>} means {@code System.getProperty(X)} with any
      * spaces in the result removed, and {@code /} stands for the
      * file separator character ({@link File#separator}).
      * <p>
      * If this method returns <code>null</code>, i.e. the libraries
      * were not found in any of the JAR files loaded with this class
      * loader, the VM searches the library along the path specified
      * as the <code>java.library.path</code> property.
      *
      * <p>
      * 返回本地库的绝对路径名VM调用此方法以查找属于使用此类加载器加载的类的本机库在JAR文件中搜索库是仅使用本机库名称,如果找不到本机库名称以及架构特定的路径名​​(<code> OSName / OSAr
      * ch / OSVersion / lib / nativelibname </code>),即。
      * <p>
      *  Solaris SPARC 57上的库统计信息将在JAR文件中搜索为：
      * <OL>
      *  <LI> libstatso <LI> SunOS / sparc / 57 / lib / libstatso
      * </OL>
      *  在Windows NT 40上的库统计将在JAR文件中搜索为：
      * <OL>
      *  <LI> statdll <LI> WindowsNT / x86 / 40 / lib / statdll
      * </OL>
      * 
      * <p>更具体地说,让{@code nativelibname} </em>是{@link System#mapLibraryName(javalangString)SystemmapLibraryName}
      *  {@ code(libname)}的结果。
      * 然后在JAR中搜索以下名称文件：<br> <@> {@ code nativelibname} </em> <br> {@code <osname> / <osarch> / <osversion> / lib /}
      *  <em> {@ code nativelibname} / em> <br>其中{@code <X>}表示{@code SystemgetProperty(X)},其中的结果中的任何空格都已删除,{@code /}
      * 表示文件分隔符({@link File#separator })。
      * <p>
      *  如果此方法返回<code> null </code>,即在使用此类加载器加载的任何JAR文件中未找到库,VM将沿指定为<code> javalibrarypath </code>属性
      * 
      * 
      * @param libname The library name.
      *
      * @return The absolute path of the native library.
      */
     protected String findLibrary(String libname) {

         String abs_path;
         String mth = "findLibrary";

         // Get the platform-specific string representing a native library.
         //
         String nativelibname = System.mapLibraryName(libname);

         //
         // See if the native library is accessible as a resource through the JAR file.
         //
         if (MLET_LOGGER.isLoggable(Level.FINER)) {
             MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                     "Search " + libname + " in all JAR files");
         }

         // First try to locate the library in the JAR file using only
         // the native library name.  e.g. if user requested a load
         // for "foo" on Solaris SPARC 5.7 we try to load "libfoo.so"
         // from the JAR file.
         //
         if (MLET_LOGGER.isLoggable(Level.FINER)) {
             MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                     "loadLibraryAsResource(" + nativelibname + ")");
         }
         abs_path = loadLibraryAsResource(nativelibname);
         if (abs_path != null) {
             if (MLET_LOGGER.isLoggable(Level.FINER)) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                         nativelibname + " loaded, absolute path = " + abs_path);
             }
             return abs_path;
         }

         // Next try to locate it using the native library name and
         // the architecture-specific path name.  e.g. if user
         // requested a load for "foo" on Solaris SPARC 5.7 we try to
         // load "SunOS/sparc/5.7/lib/libfoo.so" from the JAR file.
         //
         nativelibname = removeSpace(System.getProperty("os.name")) + File.separator +
             removeSpace(System.getProperty("os.arch")) + File.separator +
             removeSpace(System.getProperty("os.version")) + File.separator +
             "lib" + File.separator + nativelibname;
         if (MLET_LOGGER.isLoggable(Level.FINER)) {
             MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                     "loadLibraryAsResource(" + nativelibname + ")");
         }

         abs_path = loadLibraryAsResource(nativelibname);
         if (abs_path != null) {
             if (MLET_LOGGER.isLoggable(Level.FINER)) {
                 MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                         nativelibname + " loaded, absolute path = " + abs_path);
             }
             return abs_path;
         }

         //
         // All paths exhausted, library not found in JAR file.
         //

         if (MLET_LOGGER.isLoggable(Level.FINER)) {
             MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                     libname + " not found in any JAR file");
             MLET_LOGGER.logp(Level.FINER, MLet.class.getName(), mth,
                     "Search " + libname + " along the path " +
                     "specified as the java.library.path property");
         }

         // Let the VM search the library along the path
         // specified as the java.library.path property.
         //
         return null;
     }


     /*
      * ------------------------------------------
      *  PRIVATE METHODS
      * ------------------------------------------
      * <p>
      * ------------------------------------------私有方法------ ------------------------------------
      * 
      */

     private String getTmpDir() {
         // JDK 1.4
         String tmpDir = System.getProperty("java.io.tmpdir");
         if (tmpDir != null) return tmpDir;

         // JDK < 1.4
         File tmpFile = null;
         try {
             // Try to guess the system temporary dir...
             tmpFile = File.createTempFile("tmp","jmx");
             if (tmpFile == null) return null;
             final File tmpDirFile = tmpFile.getParentFile();
             if (tmpDirFile == null) return null;
             return tmpDirFile.getAbsolutePath();
         } catch (Exception x) {
             MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                     "getTmpDir", "Failed to determine system temporary dir");
             return null;
         } finally {
             // Cleanup ...
             if (tmpFile!=null) {
                 try {
                     boolean deleted = tmpFile.delete();
                     if (!deleted) {
                         MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                                 "getTmpDir", "Failed to delete temp file");
                     }
                 } catch (Exception x) {
                     MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                             "getTmpDir", "Failed to delete temporary file", x);
                 }
             }
        }
     }

     /**
      * Search the specified native library in any of the JAR files
      * loaded by this classloader.  If the library is found copy it
      * into the library directory and return the absolute path.  If
      * the library is not found then return null.
      * <p>
      *  在该类加载器加载的任何JAR文件中搜索指定的本地库如果找到该库,将其复制到库目录并返回绝对路径如果未找到库,则返回null
      * 
      */
     private synchronized String loadLibraryAsResource(String libname) {
         try {
             InputStream is = getResourceAsStream(
                     libname.replace(File.separatorChar,'/'));
             if (is != null) {
                 try {
                     File directory = new File(libraryDirectory);
                     directory.mkdirs();
                     File file = Files.createTempFile(directory.toPath(),
                                                      libname + ".", null)
                                      .toFile();
                     file.deleteOnExit();
                     FileOutputStream fileOutput = new FileOutputStream(file);
                     try {
                         byte[] buf = new byte[4096];
                         int n;
                         while ((n = is.read(buf)) >= 0) {
                            fileOutput.write(buf, 0, n);
                         }
                     } finally {
                         fileOutput.close();
                     }
                     if (file.exists()) {
                         return file.getAbsolutePath();
                     }
                 } finally {
                     is.close();
                 }
             }
         } catch (Exception e) {
             MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                     "loadLibraryAsResource",
                     "Failed to load library : " + libname, e);
             return null;
         }
         return null;
     }

   /**
    * Removes any white space from a string. This is used to
    * convert strings such as "Windows NT" to "WindowsNT".
    * <p>
    *  从字符串中删除任何空格这用于将字符串(如"Windows NT"转换为"WindowsNT"
    * 
    */
     private static String removeSpace(String s) {
         return s.trim().replace(" ", "");
     }

     /**
      * <p>This method is to be overridden when extending this service to
      * support caching and versioning.  It is called from {@link
      * #getMBeansFromURL getMBeansFromURL} when the version,
      * codebase, and jarfile have been extracted from the MLet file,
      * and can be used to verify that it is all right to load the
      * given MBean, or to replace the given URL with a different one.</p>
      *
      * <p>The default implementation of this method returns
      * <code>codebase</code> unchanged.</p>
      *
      * <p>
      * <p>当扩展此服务以支持缓存和版本控制时,将覆盖此方法当从MLet文件中提取版本,代码库和jarfile时,从{@link #getMBeansFromURL getMBeansFromURL}调用此方
      * 法,并且可以用于请验证是否可以加载给定的MBean,或者使用不同的网址替换给定的网址</p>。
      * 
      *  <p>此方法的默认实现返回<code> codebase </code>未更改</p>
      * 
      * 
      * @param version The version number of the <CODE>.jar</CODE>
      * file stored locally.
      * @param codebase The base URL of the remote <CODE>.jar</CODE> file.
      * @param jarfile The name of the <CODE>.jar</CODE> file to be loaded.
      * @param mlet The <CODE>MLetContent</CODE> instance that
      * represents the <CODE>MLET</CODE> tag.
      *
      * @return the codebase to use for the loaded MBean.  The returned
      * value should not be null.
      *
      * @exception Exception if the MBean is not to be loaded for some
      * reason.  The exception will be added to the set returned by
      * {@link #getMBeansFromURL getMBeansFromURL}.
      *
      */
     protected URL check(String version, URL codebase, String jarfile,
                         MLetContent mlet)
             throws Exception {
         return codebase;
     }

    /**
     * Loads the serialized object specified by the <CODE>OBJECT</CODE>
     * attribute of the <CODE>MLET</CODE> tag.
     *
     * <p>
     *  加载由<CODE> MLET </CODE>标签的<CODE> OBJECT </CODE>属性指定的序列化对象
     * 
     * 
     * @param codebase The <CODE>codebase</CODE>.
     * @param filename The name of the file containing the serialized object.
     * @return The serialized object.
     * @exception ClassNotFoundException The specified serialized
     * object could not be found.
     * @exception IOException An I/O error occurred while loading
     * serialized object.
     */
     private Object loadSerializedObject(URL codebase, String filename)
             throws IOException, ClassNotFoundException {
        if (filename != null) {
            filename = filename.replace(File.separatorChar,'/');
        }
        if (MLET_LOGGER.isLoggable(Level.FINER)) {
            MLET_LOGGER.logp(Level.FINER, MLet.class.getName(),
                    "loadSerializedObject", codebase.toString() + filename);
        }
        InputStream is = getResourceAsStream(filename);
        if (is != null) {
            try {
                ObjectInputStream ois = new MLetObjectInputStream(is, this);
                Object serObject = ois.readObject();
                ois.close();
                return serObject;
            } catch (IOException e) {
                if (MLET_LOGGER.isLoggable(Level.FINEST)) {
                    MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                            "loadSerializedObject",
                            "Exception while deserializing " + filename, e);
                }
                throw e;
            } catch (ClassNotFoundException e) {
                if (MLET_LOGGER.isLoggable(Level.FINEST)) {
                    MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                            "loadSerializedObject",
                            "Exception while deserializing " + filename, e);
                }
                throw e;
            }
        } else {
            if (MLET_LOGGER.isLoggable(Level.FINEST)) {
                MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                        "loadSerializedObject", "Error: File " + filename +
                        " containing serialized object not found");
            }
            throw new Error("File " + filename + " containing serialized object not found");
        }
     }

     /**
      * Converts the String value of the constructor's parameter to
      * a basic Java object with the type of the parameter.
      * <p>
      *  将构造函数的参数的String值转换为具有参数类型的基本Java对象
      */
     private  Object constructParameter(String param, String type) {
         // check if it is a primitive type
         Class<?> c = primitiveClasses.get(type);
         if (c != null) {
            try {
                Constructor<?> cons =
                    c.getConstructor(String.class);
                Object[] oo = new Object[1];
                oo[0]=param;
                return(cons.newInstance(oo));

            } catch (Exception  e) {
                MLET_LOGGER.logp(Level.FINEST, MLet.class.getName(),
                        "constructParameter", "Got unexpected exception", e);
            }
        }
        if (type.compareTo("java.lang.Boolean") == 0)
             return Boolean.valueOf(param);
        if (type.compareTo("java.lang.Byte") == 0)
             return new Byte(param);
        if (type.compareTo("java.lang.Short") == 0)
             return new Short(param);
        if (type.compareTo("java.lang.Long") == 0)
             return new Long(param);
        if (type.compareTo("java.lang.Integer") == 0)
             return new Integer(param);
        if (type.compareTo("java.lang.Float") == 0)
             return new Float(param);
        if (type.compareTo("java.lang.Double") == 0)
             return new Double(param);
        if (type.compareTo("java.lang.String") == 0)
             return param;

        return param;
     }

    private synchronized void setMBeanServer(final MBeanServer server) {
        this.server = server;
        PrivilegedAction<ClassLoaderRepository> act =
            new PrivilegedAction<ClassLoaderRepository>() {
                public ClassLoaderRepository run() {
                    return server.getClassLoaderRepository();
                }
            };
        currentClr = AccessController.doPrivileged(act);
    }

}