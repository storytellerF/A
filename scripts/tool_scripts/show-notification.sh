#!/bin/bash
# 跨平台通知显示脚本
# 参数: $1=标题, $2=消息, $3=是否成功(true/false)

title="$1"
message="$2"
is_success="$3"

if [[ "$(uname -s)" =~ MINGW|CYGWIN|MSYS ]]; then
  # Windows Dialog
  if [ "$is_success" = "true" ]; then
    powershell.exe -Command "
      Add-Type -AssemblyName PresentationFramework;
      Add-Type -AssemblyName WindowsBase;
      [System.Media.SystemSounds]::Asterisk.Play();
      \$window = New-Object System.Windows.Window;
      \$window.Title = '$title';
      \$window.Width = 450;
      \$window.Height = 180;
      \$window.WindowStartupLocation = 'CenterScreen';
      \$window.Topmost = \$true;
      \$textBlock = New-Object System.Windows.Controls.TextBlock;
      \$textBlock.Text = '$message';
      \$textBlock.Margin = '30,20,30,0';
      \$textBlock.TextWrapping = 'Wrap';
      \$textBlock.HorizontalAlignment = 'Center';
      \$textBlock.VerticalAlignment = 'Top';
      \$textBlock.FontSize = 14;
      \$button = New-Object System.Windows.Controls.Button;
      \$button.Content = 'OK';
      \$button.Width = 100;
      \$button.Height = 32;
      \$button.Margin = '0,0,0,25';
      \$button.HorizontalAlignment = 'Center';
      \$button.VerticalAlignment = 'Bottom';
      \$button.Add_Click({ \$window.Close() });
      \$grid = New-Object System.Windows.Controls.Grid;
      \$grid.Children.Add(\$textBlock);
      \$grid.Children.Add(\$button);
      \$window.Content = \$grid;
      \$window.Activate();
      \$window.ShowDialog() | Out-Null;
    "
  else
    powershell.exe -Command "
      Add-Type -AssemblyName PresentationFramework;
      Add-Type -AssemblyName WindowsBase;
      [System.Media.SystemSounds]::Hand.Play();
      \$window = New-Object System.Windows.Window;
      \$window.Title = '$title';
      \$window.Width = 450;
      \$window.Height = 180;
      \$window.WindowStartupLocation = 'CenterScreen';
      \$window.Topmost = \$true;
      \$textBlock = New-Object System.Windows.Controls.TextBlock;
      \$textBlock.Text = '$message';
      \$textBlock.Margin = '30,20,30,0';
      \$textBlock.TextWrapping = 'Wrap';
      \$textBlock.HorizontalAlignment = 'Center';
      \$textBlock.VerticalAlignment = 'Top';
      \$textBlock.FontSize = 14;
      \$button = New-Object System.Windows.Controls.Button;
      \$button.Content = 'OK';
      \$button.Width = 100;
      \$button.Height = 32;
      \$button.Margin = '0,0,0,25';
      \$button.HorizontalAlignment = 'Center';
      \$button.VerticalAlignment = 'Bottom';
      \$button.Add_Click({ \$window.Close() });
      \$grid = New-Object System.Windows.Controls.Grid;
      \$grid.Children.Add(\$textBlock);
      \$grid.Children.Add(\$button);
      \$window.Content = \$grid;
      \$window.Activate();
      \$window.ShowDialog() | Out-Null;
    "
  fi
else
  # Linux/macOS 通知
  if command -v notify-send >/dev/null 2>&1; then
    # Linux (需要 libnotify-bin)
    if [ "$is_success" = "true" ]; then
      notify-send -u normal -i dialog-information "$title" "$message"
      paplay /usr/share/sounds/freedesktop/stereo/complete.oga 2>/dev/null || true
    else
      notify-send -u critical -i dialog-error "$title" "$message"
      paplay /usr/share/sounds/freedesktop/stereo/dialog-error.oga 2>/dev/null || true
    fi
  elif command -v osascript >/dev/null 2>&1; then
    # macOS
    osascript -e "display notification \"$message\" with title \"$title\" sound name \"$( [ \"$is_success\" = \"true\" ] && echo 'Glass' || echo 'Basso' )\""
  fi
fi
