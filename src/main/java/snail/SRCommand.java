/*
 * Copyright JinxiuYang from UESTC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package snail;

import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;

/**
 * Sample Apache Karaf CLI command
 */
@Command(scope = "onos", name = "sr",
        description = "SR Apache Karaf CLI command")
public class SRCommand extends AbstractShellCommand {

    //formatted string for output to CLI
    private static final String FMT = "%s";

    private SRService srService;

    @Override
    protected void execute() {
        srService = get(SRService.class);
        srService.SR();
        print("%s", "Finished!");
    }

}