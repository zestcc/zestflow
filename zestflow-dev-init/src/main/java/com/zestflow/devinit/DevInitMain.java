package com.zestflow.devinit;

/**
 * {@code --init-dev} 独立入口（Java 8+）。
 */
public final class DevInitMain {

    private DevInitMain() {
    }

    public static void main(String[] args) throws Exception {
        DevInitCommandParser.printResult(DevInitCommandParser.run(args));
    }
}
