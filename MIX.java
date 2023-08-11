package mix;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

//MIX汇编程序（TOACP卷1 1.3.2节）
class MIXAL {
	MIX machine = new MIX();									//MIX虚拟机
	HashMap<String, Long> global = new HashMap<>();				//全局变量
	HashMap<String,Long> local = new HashMap<>();				//局部变量
	HashSet<String> set = new HashSet<>();						//伪运算符
	HashMap<String, ArrayList<Long>> future = new HashMap<>(); 	//向前引用的符号
	HashMap<String, ArrayList<Long>> cons = new HashMap<>(); 	//定义常量
	HashMap<String,String> equ = new HashMap<>();				//EQU
	long lc;													//地址计数器
	
	MIXAL() {
		set.add("ORIG");
		set.add("EQU");
		set.add("CON");
		set.add("END");
	}
	
	MIXAL(MIX machine, String s) {
		set.add("ORIG");
		set.add("EQU");
		set.add("CON");
		set.add("ALF");
		set.add("END");
		this.machine = machine;
		process(s);
	}
	
	boolean isNum(String s) {
		return s.matches("^-?\\d+");
	}
	
	boolean isSymbol(String s) {
		return s.matches("[a-zA-Z0-9]*[a-zA-Z][a-zA-Z0-9]*");
	}
	
	int symbol(String s) {
		//符号类型
		if (s.matches("\\d[FBH]")) {
			//0H-9H
			return Integer.parseInt(s.substring(0,1));
		}
		if(s.matches("[a-zA-Z0-9]*[a-zA-Z][a-zA-Z0-9]*"))
			return 10;
		return -1;
	}
	
	int expr(StringBuffer s) {
		//s是否为表达式
		if(s.toString().matches(".+\\+.+"))
			return '+';
		if(s.toString().matches(".+\\-.+"))
			return '-';
		if(s.toString().matches(".+\\/\\/.+")) {
			int tmp = s.indexOf("/");
			s.replace(tmp,tmp+2,"|");
			return '|';
		}
		if(s.toString().matches(".+\\/.+"))
			return '/';
		return 0;
	}
	
	long expr(String op1, String op2, int operator) {
		//计算op1和op2的运算结果
		long num1, num2;
		if(isSymbol(op1))
			num1 = wval(equ.get(op1));
		else if(op1.equals("*")) {
			num1 = this.lc;
		}
		else 
			num1 = Integer.parseInt(op1);
		if(isSymbol(op2))
			num2 = wval(equ.get(op2));
		else
			num2 = Integer.parseInt(op2);
		switch(operator) {
			case '+':
				return num1+num2;
			case '-':
				return num1-num2;
			case '/':
				return num1/num2;
			case '|':
				return num1 * Word.size() / num2;
			default:
				System.out.println("Not supported operator " + (char)operator);
				break;
		}
		return 0;
	}
	
	long wval(String s) {
		//计算wval
		long val;
		if(s.indexOf('(') >= 0) {
			//E(F)格式
			int lp = s.indexOf('(');
			int left = Integer.parseInt(s.substring(lp+1,s.indexOf(':')));
			int right = Integer.parseInt(s.substring(s.indexOf(':')+1,s.indexOf(')')));
			val = Long.parseLong(s.substring(0,lp));
			for(int i=0;i<5-right;i++) {
				val *= MyByte.SIZE;
			}
		}
		else if(s.indexOf(':') >=0 ) {
			//仅F字段
			int left = Integer.parseInt(s.substring(0,s.indexOf(':')));
			int right = Integer.parseInt(s.substring(s.indexOf(':')+1));
			val = 8*left + right;
		}
		else if(expr(new StringBuffer(s))>0) {
			int operator = expr(new StringBuffer(s));
			val = expr(s.substring(0,s.indexOf(operator)),s.substring(s.indexOf(operator)+1),operator);
		}
		else {
			//默认数字
			val = Long.parseLong(s);
		}
		return val;
	}
	
	//汇编语言：[LOC] OP ADDRESS 
	int process(String str) {
		//OP ADDRESS,I(F)
		String[] lines = str.split("\n");
		int len = lines.length;
		for (int i = 0; i < len; i++) {
			String temp = lines[i].trim();
			String[] line = temp.split(" ");
			int c = 0;	//第一列
			if(line[0].equals("*"))			//注释
				continue;
			//LOC字段
			if(machine.map.get(line[c]) == null && !set.contains(line[c])) {	
				int type = symbol(line[c]);
				if(type >= 10) {
					//全局变量
					if(global.get(line[c]) != null) 
						System.out.println("Symbol defined: " + line[c]);
					global.put(line[c], lc);
				}
				else if(type >= 0) {
					//局部变量
					String tmp = line[c].substring(0,1);
					if(local.get(tmp) != null) 
						System.out.println("Symbol defined: " + tmp);
					local.put(tmp,lc);
					future.forEach(new BiConsumer<>() {
							public void accept(String key, ArrayList<Long> list) {
								if(key.equals(tmp)) {
									for(long i : list) {
										((MIX.Ins)(machine.memory[(int)i])).setAddr(lc);
									}
								}	
							}
						}
					);
					future.remove(tmp);
				}
				else  
					System.out.println("Not a symbol: " + line[c]);
				c++;
			}
			
			//OP字段
			if(line[c].equals("EQU")) {			
				if(c > 0) //如果LOC字段不为空
					equ.put(line[0],line[c+1]);
			}
			else if(line[c].equals("ORIG")) {	
				String s = line[c+1];
				if(isNum(s)) 			//s是数字
					lc = Integer.parseInt(s);	//地址计数器设置为地址值
				else if(expr(new StringBuffer(s))>0) {
					int operator = expr(new StringBuffer(s));
					lc = expr(s.substring(0,s.indexOf(operator)),s.substring(s.indexOf(operator)+1),operator);
				}
				else if(isSymbol(s)) {
					lc = Long.parseLong(equ.get(s));
				}
			}
			else if(line[c].equals("CON")) {
				String s = line[c+1];
				Word w = new Word();
				int operator;
				StringBuffer sb = new StringBuffer(s);
				if(isNum(s)) {
					//s是数字
					w.setValue(Long.parseLong(s));
				} 
				else if((operator = expr(sb))>0) {
					s = sb.toString();
					long res = expr(s.substring(0,s.indexOf(operator)),s.substring(s.indexOf(operator)+1), operator);
					w.setValue(res);
				}
				machine.memory[(int)lc++] = w;
			}
			else if(line[c].equals("ALF")) {
				//ALF
			}
			else if(line[c].equals("END")) {
				//程序结束
			}
			else {
				//OP A,I(F)
				MIX.Ins ins = machine.map.get(line[c]);
				if (ins != null) {
					MIX.Ins word = machine.new Ins();
					word.setOpCode(ins.code);			//写入操作码
					word.setField(ins.field);			
					//写入字段值
					machine.memory[(int)lc] = word;
					if(line.length > c+1) {
						Long addr;
						int p = line[c+1].indexOf('('); //I和F的位置
						if(p > 0) {
							//指令中是否有F字段 
							int q = line[c+1].indexOf(')'); 
							String s = line[c+1].substring(p+1,q);
						
							if(s.indexOf(':') >= 0) {
								String[] f = s.split(":");
								word.setField(8*Integer.parseInt(f[0]) + Integer.parseInt(f[1]));
							}
							else if(isSymbol(s)) {
								//处理一般的符号
								word.setField((int)wval(equ.get(s)));
							}
						}
						if(line[c+1].indexOf(',') > 0) {
							//指令中是否有I字段
							p = line[c+1].indexOf(',');
							String s = line[c+1].substring(p+1, p+2);
							word.setIndex(Integer.parseInt(s));
						}
						String s;	//地址字段
						if (p > 0) 
							s = line[c+1].substring(0,p);
						else
							s = line[c+1];
						
						if(isNum(s)) {
							//A是一个数字
							addr = Long.parseLong(s);	
							word.setAddr(addr.intValue());
						} 
						else if(s.equals("*")) {
							word.setAddr(lc);
						}
						else if(symbol(s) >= 0) {
							//A是一个符号
							int type = symbol(s);
							boolean f = false;
							if(type >= 10) {
								//全局符号 
								if(equ.get(s) != null) {
									word.setAddr(wval(equ.get(s)));
								}
								else if((addr = global.get(s)) != null)
									word.setAddr(addr.longValue());
								else 
									f = true;
							}
							else {
								//局部符号
								if(s.substring(1).equals("B"))
									word.setAddr(local.get(s.substring(0,1)).longValue());
								else {
									f = true;
									s = s.substring(0,1);
								}
							}
							if(f) {
								//备查符号
								ArrayList<Long> list = future.get(s);
								if(list != null) 
									list.add(lc);
								else {
									list = new ArrayList<>();
									list.add(lc);
									future.put(s,list);
								}
							}
						}
						else if(s.matches("^=.*=$")) {
							//A是字面常量
							String con = s.substring(1,s.length()-1);
							ArrayList<Long> list = cons.get(con);
							if(list != null) {
								list.add(lc);
							}
							else {
								list = new ArrayList<>();
								list.add(lc);
								cons.put(con, list);
							}
						}
						else if(expr(new StringBuffer(s)) > 0) {
							//A是表达式
							int operator = expr(new StringBuffer(s));
							long res = expr(s.substring(0,s.indexOf(operator)),s.substring(s.indexOf(operator)+1), operator);
							word.setAddr(res);
						}
					}
					lc++;
				}
				else {
					System.out.println("Error: unknown op code " + line[c]);
				}
			}			
		}
		//处理向前定义的符号
		Iterator<String> iter = future.keySet().iterator();
		while(iter.hasNext()) {
			String key = iter.next();
			long value = global.get(key).longValue();
			Iterator<Long> lit = future.get(key).iterator();
			while(lit.hasNext()) {
				long counter = lit.next();
				MIX.Ins word = (MIX.Ins)machine.memory[(int)counter];
				word.setAddr(value);
			}
		}
		//生成指定的常量
		cons.forEach(new BiConsumer<>() {
				public void accept(String key, ArrayList<Long> list) {
					String con = key;
					
					Word w = new Word();
					if(isNum(con)) {
						w.setValue(Long.parseLong(con));
					}
					else if(isSymbol(con)) {
						w.setValue(wval(equ.get(con)));
					}
					else if(expr(new StringBuffer(con))>0) {
						int operator = expr(new StringBuffer(con));
						long res = expr(con.substring(0,con.indexOf(operator)), 
									   con.substring(con.indexOf(operator)+1),  
									   operator);
						w.setValue(res);
					}
					machine.memory[(int)lc] = w;
					for(long ins: list) {
						((MIX.Ins)machine.memory[(int)ins]).setAddr(lc);
					}
					lc++;
				}
			}
		);
		return 0;
	}
	
	void run() {
		machine.run();
	}
	
	public static void main(String[] args) {
		
	}
}

//MIX虚拟机(TOACP卷1 1.3节)
class MIX {
	//虚拟机指令
	class Ins extends Word {
		//符号
		static byte SIGN = 0;
		//操作码
		static byte CODE = 5;
		//字段说明 (L:R) = 8L + R
		static byte FLD = 4;
		//变址寄存器
		static byte IDX = 3;
		//内存地址
		static byte[] ADDR = {1,2};
		int code;
		int field;	
		int index;
		int address;
		int sign;
		int cost;
		
		Ins() {
			super();	
		}
		
		Ins(int code, int f,int i, int addr, int s, int cost) {
			this.code = code;
			this.field = f;
			this.index = i;
			this.address = addr;
			this.sign = s;
			this.cost = cost;
		}
	
		void setOpCode(int code) {
			value[CODE].setValue(code);
		}
	
		void setField(int f) {
			MyByte b = new MyByte();
			b.setValue(f);
			value[FLD] = b; 
		}
	
		void setIndex(int i) {
			value[IDX].setValue(i);
		}

		int getIndex() {
			return value[IDX].value;
		}

		int getField() {
			return value[FLD].value;
		}

		int getOpCode() {
			return value[CODE].value;
		}
	
		void setAddr(long addr) {
			setValue(addr,0,2);
		}
	
		long getAddr() {
			return getValue(0,2);
		}
		
		void setSign(int sign) {
			value[SIGN].setValue(sign);
		}
		
		int getSign() {
			return value[SIGN].value;
		}
	}
	long pc;	//程序计数器	
	Word[] memory = new Word[10000];				//MIX内存
	Word registerA = new Word();					//寄存器A
	Word registerX = new Word();					//寄存器X
	Word registerJ = new Word();					//跳转寄存器
	Word[] registerI = new Word[6];					//idx寄存器
	HashMap<String, Ins> map = new HashMap<>();		//指令编码
	int overflow;									//溢出寄存器
	int comparator;									//比较器(-1,0,1)
	IO lp = new LinePrinter(memory);				//行打印机
	
	MIX() {
		//参数从左到右依次为操作码 域字段 索引 地址 符号 耗时
		map.put("NOP", new Ins(0,0,0,0,1,1));
		map.put("ADD", new Ins(1,5,0,0,1,2));
		map.put("SUB", new Ins(2,5,0,0,1,2));
		map.put("MUL", new Ins(3,5,0,0,1,10));
		map.put("DIV", new Ins(4,5,0,0,1,12));
		map.put("CHAR", new Ins(5,1,0,0,1,10));
		map.put("HLT", new Ins(5,2,0,0,1,10));
		map.put("SLA", new Ins(6,0,0,0,1,2));
		map.put("SRA", new Ins(6,1,0,0,1,2));
		map.put("SLAX", new Ins(6,2,0,0,1,2));
		map.put("SRAX", new Ins(6,3,0,0,1,2));
		map.put("SLC", new Ins(6,4,0,0,1,2));
		map.put("SRC", new Ins(6,5,0,0,1,2));
		map.put("LDA", new Ins(8,5,0,0,1,2));
		map.put("LD1", new Ins(9,5,0,0,1,2));
		map.put("LD2", new Ins(10,5,0,0,1,2));
		map.put("LD4", new Ins(12,5,0,0,1,2));
		map.put("LDX", new Ins(15,5,0,0,1,2));
		map.put("LDAN", new Ins(16,5,0,0,1,2));
		map.put("LD1N", new Ins(17,5,0,0,1,2));
		map.put("STA", new Ins(24,5,0,0,1,2));
		map.put("ST2", new Ins(26,5,0,0,1,2));
		map.put("STX", new Ins(31,5,0,0,1,2));
		map.put("STJ", new Ins(32,2,0,0,1,2));
		map.put("IOC", new Ins(35,0,0,0,1,2));
		map.put("OUT", new Ins(37,0,0,0,1,2));
		map.put("JMP", new Ins(39,0,0,0,1,1));
		map.put("JOV", new Ins(39,2,0,0,1,1));
		map.put("JL", new Ins(39,4,0,0,1,1));
		map.put("JG", new Ins(39,6,0,0,1,1));
		map.put("JGE", new Ins(39,7,0,0,1,1));
		map.put("JNE", new Ins(39,8,0,0,1,1));
		map.put("JAZ", new Ins(40,1,0,0,1,2));
		map.put("JAP", new Ins(40,2,0,0,1,2));
		map.put("JANN", new Ins(40,3,0,0,1,2));
		map.put("J1N", new Ins(41,0,0,0,1,1));
		map.put("J1Z", new Ins(41,1,0,0,1,1));
		map.put("J1P", new Ins(41,2,0,0,1,1));
		map.put("J2N", new Ins(42,0,0,0,1,1));
		map.put("J3P", new Ins(43,2,0,0,1,1));
		map.put("J5N", new Ins(45,0,0,0,1,1));
		map.put("J5P", new Ins(45,2,0,0,1,1));
		map.put("JXN", new Ins(47,0,0,0,1,1));
		map.put("JXZ", new Ins(47,1,0,0,1,1));
		map.put("JXNZ", new Ins(47,4,0,0,1,1));
		map.put("JXE", new Ins(47,6,0,0,1,1));
		map.put("JXO", new Ins(47,7,0,0,1,1));
		map.put("INCA", new Ins(48,0,0,0,1,1));
		map.put("ENTA", new Ins(48,2,0,0,1,1));
		map.put("INC1", new Ins(49,0,0,0,1,1));
		map.put("INC2", new Ins(50,0,0,0,1,1));
		map.put("INC3", new Ins(51,0,0,0,1,1));
		map.put("DEC2", new Ins(50,1,0,0,1,1));
		map.put("ENT2", new Ins(50,2,0,0,1,1));
		map.put("DEC3", new Ins(51,1,0,0,1,1));
		map.put("ENT3", new Ins(51,2,0,0,1,1));
		map.put("DEC4", new Ins(52,1,0,0,1,1));
		map.put("ENT4", new Ins(52,2,0,0,1,1));
		map.put("INC5", new Ins(53,0,0,0,1,1));
		map.put("DEC5", new Ins(53,1,0,0,1,1));
		map.put("ENT5", new Ins(53,2,0,0,1,1));
		map.put("DECX", new Ins(55,1,0,0,1,1));
		map.put("ENTX", new Ins(55,2,0,0,1,1));
		map.put("CMPA", new Ins(56,5,0,0,1,2));
		
		//初始化idx寄存器
		for(int i=0;i<6;i++) {
			Word w = new Word();
			w.setValue(0,1);
			w.setValue(0,2);
			w.setValue(0,3);
			registerI[i] = w;
		}	
	}
	
	Word[] regs() {
		//返回寄存器集合rA r1...r6 rX
		Word[] regs = new Word[10];
		regs[0] = registerA;
		for(int i=1;i<7;i++) 
			regs[i] = registerI[i-1];
		regs[7] = registerX;
		regs[8] = registerJ;
		regs[9] = new Word();	//Zero
		return regs;
	}
	
	void resetPC() {
		this.pc = 0;
	}
	
	void resetRegister(int idx) {
		//将寄存器idx的值清零
		switch(idx) {
			case 0:
				registerA.clear();
				break;
			case 7:
				registerX.clear();
				break;
			default:
				registerI[idx-1].clear();
				break;
		}
	}
	
	void resetMemory(int loc) {
		memory[loc].clear();
	}
	
	void dump(int start, int size) {
		for (int i = start; i<start+size; i++) {
			if(memory[i] != null)
				dump(memory[i]);
		}
	}		
	
	void dump(Word w) {
		Ins ins = (Ins)w;
		Formatter f = new Formatter(System.out);
		f.format("%04d",ins.getAddr());
		System.out.print(" ");
		System.out.print(ins.getIndex());
		System.out.print(" ");
		System.out.print(ins.getField());
		System.out.print(" ");
		System.out.printf("%02d",ins.getOpCode());
		System.out.print("\n");
	}
	
	Word subtract(Word subtrahend, Word subtractor) {
		//subtrthend减去subtractor
		//subtrahend和subtractor均为正数
		//subtrahend大于subtractor
		long diff;
		int carry=0;
		Word res = new Word();
		for(int i=5; i>=1; i--) {
			diff = subtrahend.getValue(i) - subtractor.getValue(i) + carry;
			if (diff < 0) {
				diff += MyByte.SIZE;
				carry = -1;
			}
			else 
				carry = 0;
			res.setValue(diff, i);	
		}
		return res;	
	}
		
	Word add(Word augend, Word addend) {
		//augend加上addend,返回相加的结果
		//函数可能会设置溢出标志
		//augend和addend均为正数
		long sum;
		int carry=0;
		Word rTmp = new Word();
		for(int i=5; i>=1; i--) {
			sum = augend.getValue(i) + addend.getValue(i)+carry;
			if (sum >= MyByte.SIZE) {
				sum -= MyByte.SIZE;
				carry = 1;
			}
			else 
				carry = 0;
			rTmp.setValue(sum, i);	
		}
		this.overflow = carry;
		return rTmp;
	}
	
	Word[] multiply(Word multiplicand, Word multiplier) {
		//multiplicand乘以multiplier
		long mul;
		long carry=0;
		long tmp=0;
		Word rA = new Word();			//临时寄存器A
		Word rX = new Word();			//临时寄存器X
		Word register;					//寄存器
		int k;							//寄存器下标
		for(int i=5; i>=1; i--) {		//乘数
			carry = 0;					//进位清零
			for(int j=5; j>=1; j--) {	//被乘数
				if(i+j>5) {
					register = rX;
					k = i+j-5;
				}
				else {
					register = rA;
					k = i+j;
				}
				mul = multiplicand.getValue(j) * multiplier.getValue(i) + carry;
				carry = mul / MyByte.SIZE;
				tmp = register.getValue(k);
				tmp += mul % MyByte.SIZE;
				carry += tmp/MyByte.SIZE;
				tmp %= MyByte.SIZE;
				register.setValue(tmp,k);
			}
			rA.setValue(carry, i);
		}
		long s = multiplicand.getValue(0);	//被乘数的符号
		long t = multiplier.getValue(0);		//乘数的符号
		if (s == t) {
			rA.setValue(1,0);
			rX.setValue(1,0);
		}
		else {
			rA.setValue(-1,0);
			rX.setValue(-1,0);
		}
		return new Word[]{rA,rX};
	}

	Word[] multiply(Word multiplier,int left,int right) {
		//registerA乘以multiplier
		return multiply(registerA, multiplier,left,right);
	}
	
	//带字段的乘法
	Word[] multiply(Word multiplicand, Word multiplier, int left, int right) {
		//将multiplicand和multiplier从left到right的字段相乘
		if((left != 0) || (right != 5)) {
			Word temp = new Word();
			temp.load(multiplier,left,right);
			multiplier = temp;
		}
		return multiply(multiplicand,multiplier);
	}
	

	Word[] multiply(Word[] number, int len, long scaler) {
		//对长度为len的数字number按照scaler的大小缩放
		long carry = 0;
		Word[] res = new Word[len+1];
		for(int i=0;i<res.length;i++)
			res[i] = new Word();
		for(int i = 0; i<len;i++) {
			long mul = number[i].getValue() * scaler + carry;
			carry = mul / MyByte.BASE;
			res[i].setValue(mul % MyByte.BASE);
		}
		res[len].setValue(carry);
		return res;
	}
	
	
	Word[] divide(Word[] dividend, int n, long divisor) {
		//长度为n的被除数dividend除以一位数除数divisor
		//dividend和divisor都为正数
		long r = 0;		//余数
		long q = 0;		//商
		
		for(int i=n-1;i>=0;i--) {
			q = q*MyByte.BASE + (r*MyByte.BASE + dividend[i].getValue())/divisor;
			r = (r*MyByte.BASE + dividend[i].getValue()) % divisor;
		}
		return new Word[] {new Word(q), new Word(r)};
	}
	
	Word[] divide(Word divisor) {
		//被除数dividend除以除数divisor
		//返回商和余数
		//1.对被除数和除数进行规范化
		Word[] tmpDividend = new Word[21];
		Word[] tmpDivisor = new Word[11];
		long quotient = 0;			//商
		long remainder = 0;			//余数
		int msb = 0;
		int m = 0;			//被除数为m位
		int n = 0;			//除数为n位
		//准备数据
		for(int i=1;i<=5;i++)
			if(msb > 0 || divisor.getValue(i) > 0) {
				int h = 10-2*i+1;
				tmpDivisor[h] = new Word();
				tmpDivisor[h].setValue(divisor.getValue(i)/MyByte.BASE);
				int l = 10-2*i;
				tmpDivisor[l] = new Word();
				tmpDivisor[l].setValue(divisor.getValue(i)%MyByte.BASE);

				n+=2;
				if(msb == 0)
					msb = 1;
			}
		Word[] tmpRegs = {registerA, registerX};
		msb = 0;
		for(int i=1;i<=10;i++) {
			long digit = tmpRegs[i/6].getValue((i-1)%5+1);
			if(msb > 0 || digit > 0) {
				tmpDividend[20-2*i+1] = new Word();
				tmpDividend[20-2*i+1].setValue(digit/MyByte.BASE);
				tmpDividend[20-2*i] = new Word();
				tmpDividend[20-2*i].setValue(digit%MyByte.BASE);
				m+=2;
				if(msb == 0)
					msb = 1;
			}
		}
		if(tmpDivisor[n-1].getValue() == 0)		
			n--;
		if(tmpDividend[n-1].getValue() == 0)
			m--;
		if(n < 2) {
			//除数只有一位数
			return divide(tmpDividend, m, tmpDivisor[0].getValue());
		}
			
		//对数据规范化
		long scaler = MyByte.BASE / (tmpDivisor[n-1].getValue() + 1);	//计算缩放因子
		tmpDivisor = multiply(tmpDivisor, n, scaler);					//对除数进行规范化
		tmpDividend = multiply(tmpDividend,m,scaler);					//对被除数进行规范化
		//求商
		m -= n;														//m+n位整数除以n位整数
		for(int j=m; j>=0; j--) {
			//试算商
			long qhat = (tmpDividend[j+n].getValue() * MyByte.BASE + tmpDividend[j+n-1].getValue()) / tmpDivisor[n-1].getValue();
			long rhat = (tmpDividend[j+n].getValue() * MyByte.BASE + tmpDividend[j+n-1].getValue()) % tmpDivisor[n-1].getValue();
			while(qhat == MyByte.BASE || (qhat*tmpDivisor[n-2].getValue()) > (MyByte.BASE * rhat + tmpDividend[j+n-2].getValue())) {
				qhat--;
				rhat += tmpDivisor[n-1].getValue();
				if(rhat >= MyByte.BASE)
					break;
			}
		
			//改变被除数
			Word[] tmp = multiply(tmpDivisor, n, qhat);
			long borrow = 0;
			for(int i = j; i<=j+n; i++) {
				long diff = tmpDividend[i].getValue() - tmp[i-j].getValue() - borrow;
				if(diff < 0) {
					diff += MyByte.BASE;
					borrow = 1;
				}
				else 
					borrow = 0;
				tmpDividend[i].setValue(diff);
			}
			quotient = quotient*MyByte.BASE + qhat;
			//余数为负数
			if(borrow > 0) {
				quotient -= 1;
				long carry = 0;
				for(int i=j;i<=j+n;i++) {
					long sum = tmpDividend[i].getValue() + tmpDivisor[i].getValue() + carry;
					if (sum > MyByte.BASE) {
						sum = sum - MyByte.BASE;
						carry = 1;
					}
					else 
						carry = 0;
					tmpDividend[i].setValue(sum);
				}
			}			
		}
		
		//求余数
		long r = 0;
		for(int i=n-1;i>=0;i--) {
			long q = (r*MyByte.BASE + tmpDividend[i].getValue()) / scaler; 
			r = (r*MyByte.BASE + tmpDividend[i].getValue()) % scaler;
			remainder = remainder * MyByte.BASE + q;
		}
		return new Word[] {new Word(quotient), new Word(remainder)};
	}
	
	void shiftRight(Word left, Word right, int m, boolean cycle) {
		//将left和right右移
		Word register;
		for(int i = 1; i<=m; i++) {
			long k = registerX.getValue(5);	
			for(int j = 9; j>=1; j--) {
				if(j>5) 
					registerX.setValue(registerX.getValue(j-5),j-4);
				else if(j == 5) {
					registerX.setValue(registerA.getValue(5),1);
				}
				else 
					registerA.setValue(registerA.getValue(j),j+1);
			}
			if(cycle) 
				registerA.setValue(k,1);
			else
				registerA.setValue(0,1);
		}	
	}
	
	void shiftLeft(Word left, Word right, int m, boolean cycle) {
		//将left和right左移
		Word register;
		for(int i = 1; i<=m;i++) {
			long k = registerA.getValue(1);	
			for(int j = 1; j<=9; j++) {
				if(j<5) 
					registerA.setValue(registerA.getValue(j+1),j);
				else if(j == 5) {
					registerA.setValue(registerX.getValue(1),5);
				}
				else 
					registerX.setValue(registerX.getValue(j-4),j-5);
			}
			if(cycle) 
				registerX.setValue(k,5);
			else
				registerX.setValue(0,5);
		}	
	}
	
	void shiftRight(Word w, int m, boolean cycle) {
		//将w向右移动m位
		for(int i=1;i<=m;i++) {
			long k = w.getValue(5);
			for(int j=5;j>=2;j--)
				w.setValue(w.getValue(j-1), j);
			if(cycle)
				w.setValue(k,1);
			else
				w.setValue(0,1);
		}
	}
	
	void shiftLeft(Word w, int m, boolean cycle) {
		//将w向左移动m位
		for(int i=1; i<=m; i++) {
			long k=w.getValue(1);
			for(int j=1;j<5;j++)
				w.setValue(w.getValue(j+1), j);
			if(cycle)
				w.setValue(k,5);
			else 
				w.setValue(0,5);
		}
	}
		
	void shiftRightAX(int m) {
		shiftRight(registerA,registerX,m,false);
	}
	
	void shiftLeftA(int m) {
		shiftLeft(registerA,m,false);
	}
	
	void shiftRightA(int m) {
		shiftRight(registerA,m,false);
	}
	
	void shiftLeftC(int m) {
		shiftLeft(registerA, registerX, m, true);
	}
	
	void shiftRightC(int m) {
		shiftRight(registerA, registerX, m, true);
	}
	
	void shiftLeftAX(int m) {
		shiftLeft(registerA, registerX, m, false);
	}
	
	void shift(int type, int m) {
		//type类型移位m个字节
		switch(type) {
			case 0:
				shiftLeftA(m);
				break;
			case 1:
				shiftRightA(m);
				break;
			case 2:
				shiftLeftAX(m);
				break;
			case 3:
				shiftRightAX(m);
				break;
			case 4:
				shiftLeftC(m);
				break;
			case 5:
				shiftRightC(m);
				break;
			default:
				break;
		}
	}
					
	void jump(int loc) {
		registerJ.setValue(++pc);
		pc = loc;
	}
	
	void jumpSaveJ(int loc) {
		pc = loc;
	}
		
	void jumpNonnegative(Word reg, int loc) {
		long value = reg.getValue();
		if(value >= 0) 
			jump(loc);
	}
	
	void jumpOverflow(int loc) {
		//溢出跳转
		if(this.overflow > 0) {
			this.overflow = 0;
			jump(loc);
		}
	}
	
	void jumpNoOverflow(int loc) {
		//溢出跳转
		if(this.overflow <= 0) {
			jump(loc);
		}
	}
	
	void jumpZero(Word reg, int loc) {
		//寄存器为0则跳转
		if(reg.getValue() == 0)
			jump(loc);
	}
	
	void jumpNonzero(Word reg, int loc) {
		//寄存器reg非零则跳转
		if(reg.getValue() != 0)
			jump(loc);
	}
	
	void jumpGreaterOrEqual(int loc) {
		//大于等于跳转
		if(comparator >= 0) 
			jump(loc);
	}
	
	void jumpNotEqual(int loc) {
		//不相等跳转
		if(comparator != 0) 
			jump(loc);
	}
	
	void jumpLess(int loc) {
		//小于跳转
		if(comparator < 0) 
			jump(loc);
	}
	
	void jumpGreater(int loc) {
		//大于跳转
		if(comparator > 0) 
			jump(loc);
	}

	void jumpEven(Word reg, int loc) {
		//偶数跳转
		if(reg.getValue(5) % 2 == 0) 
			jump(loc);
	}
	
	void jumpOdd(Word reg, int loc) {
		//奇数跳转
		if(reg.getValue(5) % 2 == 1) 
			jump(loc);
	}
	
	void jumpNegative(Word reg, int loc) {
		//负数跳转
		if(reg.getValue() < 0) 
			jump(loc);
	}

	void jumpPositive(Word reg, int loc) {
		//正数跳转
		if(reg.getValue() > 0) 
			jump(loc);
	}

	void jumpNonpositive(Word reg, int loc) {
		//非正数跳转
		if(reg.getValue() <= 0) 
			jump(loc);
	}

	
	void jump(int type, int m) {
		//根据跳转类型跳转到m
		switch(type) {
			case 0:
				jump(m);
				break;
			case 1:
				jumpSaveJ(m);
				break;
			case 2:
				jumpOverflow(m);
				break;
			case 3:
				jumpNoOverflow(m);
				break;
			case 4:
				jumpLess(m);
				break;
			case 6:
				jumpGreater(m);
				break;
			case 7:
				jumpGreaterOrEqual(m);
				break;
			case 8:
				jumpNotEqual(m);
				break;
			default:
				break;
		}
	}
		
	void regJump(int idx, int type, int m) {
		//寄存器idx根据跳转类型type跳转到m
		switch(type) {
			case 0:
				jumpNegative(regs()[idx], m);
				break;
			case 1:
				jumpZero(regs()[idx],m);
				break;
			case 2:
				jumpPositive(regs()[idx],m);
				break;
			case 3:
				jumpNonnegative(regs()[idx],m);
				break;
			case 4:
				jumpNonzero(regs()[idx],m);
				break;
			case 5:
				jumpNonpositive(regs()[idx],m);
				break;
			case 6:
				jumpEven(regs()[idx],m);
				break;
			case 7:
				jumpOdd(regs()[idx],m);
				break;
			default:
				break;
		}
	}
	
	Word addOrSubtract(Word first, Word second, int op) {
		//根据first和second的符号判断加或者减运算
		//op等于零表示加法，大于零表示减法
		Word res;
		
		if(op > 0) 
			second.setValue(-1*second.getValue(0),0);
		if(first.getValue(0) != second.getValue(0)) {
			//两个数相减
			if(first.getValue(1,5) < second.getValue(1,5)) {
				Word tmp = first;
				first = second;
				second = tmp;
			}
			//first大于second
			res = subtract(first, second);
		}
		else {
			//两个数相加
			res = add(first,second);
		}
		res.setValue(first.getValue(0),0);
		return res;
	}
	
	void increase(Word reg, int m) {
		//reg增加m
		Word w = new Word();
		w.setValue(m);
		reg.load(addOrSubtract(reg,w,0));
	}
	
	void decrease(Word reg, int m) {
		//reg减少m
		Word w = new Word();
		w.setValue(m);
		reg.load(addOrSubtract(reg,w,1));
	}
	
	void addressTransfer(int idx, int type, int m) {
		//地址传输指令ENTx INCx DECx
		//针对idx寄存器执行type类指令,输入参数m
		switch(type) {
			case 0:
				increase(regs()[idx], m);
				break;
			case 1:
				decrease(regs()[idx], m);
				break;
			case 2:
				enter(regs()[idx], m);
				break;
			case 3:
				enterNegative(regs()[idx],m);
				break;
			default:
				break;
		}
	}
	
	void load(int idx, int m, int left, int right) {
		//将内存m的值加载到寄存器idx，从left字段到right字段
		regs()[idx].load(memory[m],left,right);
	}
	
	void loadNegative(int idx, int m, int left, int right) {
		//将内存m的负值加载到寄存器idx，从left字段到right字段
		regs()[idx].load(memory[m],left,right);
		long sign = -1*regs()[idx].getValue(0);
		regs()[idx].setValue(sign, 0);
	}	
	
	void store(int idx, int m, int left, int right) {
		//将寄存器idx的值存储到内存m的left到right字段
		if(memory[m] == null)
			memory[m] = new Word();
		regs()[idx].store(memory[m],left,right);
	}

	void enter(Word reg, int m) {
		//reg寄存器中放入m
		reg.enter(m);
	}
	
	void enterNegative(Word reg, int m) {
		//reg中放入-m
		reg.enter(-m);
	}
	
	void compare(int idx, int m, int left, int right) {
		//寄存器idx的和内存m的值比较，对应left到right字段
		if(regs()[idx].getValue(left,right) > memory[m].getValue(left,right))
			comparator = 1;
		else if(regs()[idx].getValue(left,right) == memory[m].getValue(left,right))
			comparator = 0;
		else
			comparator = -1;
	}
	
	void toChar() {
		//将rA中的数字转换为编码对应的字符
		//结果放到rA和rX寄存器
		Word[] regAX = {registerA, registerX};
		int j = 10;	//寄存器下标
		for(int i=5;i>0;i--) {
			int byt = registerA.getValue(i);
			regAX[j/6].setValue(30+byt%MyByte.BASE,(j--+1)%6);
			if(j == 5)
				j--;
			regAX[j/6].setValue(30+byt/MyByte.BASE,(j--+1)%6);
	}
	
	void run(long pc) {
		//从pc位置开始运行
		this.pc = pc;
		run();
	}
	
	void run() {
		//虚拟机控制程序
		halt:
		while((memory[(int)pc] != null) && (memory[(int)pc] instanceof Ins)) {
			Ins w = (Ins)memory[(int)pc++];
			long sign = w.getSign();
			int m = (int)w.getAddr();
			int idx = w.getIndex();
			if(idx>0)
				m += registerI[idx-1].getValue();
			int op = w.getOpCode();
			int fld = w.getField();
			int left = fld / 8;
			int right = fld % 8;		//字段左右边界
			switch(op) {
				case 0:
					//NOP
					break;
				case 1:
					//ADD(FADD)
					registerA.load(addOrSubtract(registerA, memory[m], 0));
					break;
				case 2:
					//SUB(FADD)
					registerA.load(addOrSubtract(registerA, memory[m], 1));
					break;
				case 3:
					//MUL(FMUL)
					Word[] res = multiply(memory[m],left,right); 
					registerA.load(res[0]);
					registerX.load(res[1]);
					break;
				case 4:
					//DIV(FDIV)
					res = divide(memory[m]);
					registerA.load(res[0]);
					registerX.load(res[1]);
					break;
				case 5:
					//Special(NUM CHAR HLT)
					if(fld == 1) 
						toChar();
					else if(fld == 2)
						break halt;
					break;
				case 6:
					//shift
					shift(fld,m);
					break;
				case 7:
					//move
					break;
				case 8: 
				case 9: 
				case 10:
				case 11:
				case 12:
				case 13:
				case 14:
				case 15:
					//LDx
					load(op-8,m,left,right);
					break;
				case 16:
				case 17:
				case 18:
				case 19:
				case 20:
				case 21:
				case 22:
				case 23:
					//LDxN 
					loadNegative(op-16,m,left,right);
					break;
				case 24:
				case 25:
				case 26:
				case 27:
				case 28:
				case 29:
				case 30:
				case 31:
				case 32:
				case 33:
					//STx
					store(op-24,m,left,right);
					break;
				case 34:
					//JBus
					break;
				case 35:
					//IOC
					if(fld == 18) {
						try {
							while(!lp.ioc())
								TimeUnit.MILLISECONDS.sleep(100);
						} catch(InterruptedException e) {
							System.err.println("Interrupted");
						}
					}
					break;
				case 36:
					//Input
					break;
				case 37:
					//Output
					if(fld == 18) {
						lp.out(m);
					break;
				case 38:
					//Jready
					break;
				case 39:
					//Jump
					jump(fld,m);
					break;
				case 40:
				case 41:
				case 42:
				case 43:
				case 44:
				case 45:
				case 46:
				case 47:
					//Jx[+]
					regJump(op-40, fld, m);
					break;
				case 48:	
				case 49:	
				case 50:	
				case 51:	
				case 52:
				case 53:
				case 54:
				case 55:	
					//INCx DECx ENTx ENNx
					addressTransfer(op-48,fld,m);
					break;
				case 56:
				case 57:
				case 58:
				case 59:
				case 60:
				case 61:
				case 62:
				case 63:
					//CMPx
					compare(op-56, m,left,right);
					break;
				default:
					System.out.println("Unimplemented code: " + op); 
					break;
			}
		}
	}
}
