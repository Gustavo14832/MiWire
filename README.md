# 🎮 Miware (Fork do Starwire para Xiaomi Mi Box S Gen 3)

<div align="center">

**O cliente de streaming Moonlight hiper-calibrado para a Xiaomi Mi Box S (3ª Geração) a 1080p @ 60 Hz.**

[![Licença: GPL-3.0](https://img.shields.io/badge/licença-GPL--3.0-EF9421?style=flat-square)](LICENSE.txt)
[![Android TV 11+](https://img.shields.io/badge/Android%20TV-11%2B%20(Google%20TV)-34A853?style=flat-square&logo=android&logoColor=white)](release/Miware.apk)
[![Target: Mi Box S Gen 3](https://img.shields.io/badge/Target-Mi%20Box%20S%20Gen%203-FF6700?style=flat-square)](release/Miware.apk)
[![Package: com.miware](https://img.shields.io/badge/package-com.miware-blue?style=flat-square)](release/Miware.apk)

### [⬇ Baixar Miware.apk (Download Direto)](release/Miware.apk)
**Versão:** `0.3.0` | **Tamanho:** `~14.6 MB` | **ID do Pacote:** `com.miware`  
**SHA-256:** `572CEBCEB5D47D410091CDBF2088762E2AE523C93A3D7DE0D5D42F7F1CF975FF`

</div>

---

> [!CAUTION]
> ### ⚠️ AVISO DE COMPATIBILIDADE ESPECÍFICA (LEIA ANTES DE INSTALAR)
> Este aplicativo é uma versão **especializada e altamente calibrada** projetada exclusivamente para a **Xiaomi Mi Box S de 3ª Geração (SoC Amlogic S905X5M / S905Y4, Android TV / Google TV)** operando em telas **1080p a 60 Hz**.
> 
> As otimizações de baixo nível presentes neste APK — como o controle de bypass do Hardware Composer da Amlogic, a sincronização de fase de VSync cravada em 16.66 ms, o acumulador de mouse para Android TV e a trava de modo de desempenho contínuo — foram criadas especificamente para contornar limitações e comportamentos do driver da Amlogic.
> 
> **Em celulares, tablets ou aparelhos com telas de 90 Hz, 120 Hz ou VRR, este APK se comportará de forma inadequada (podendo causar engasgos e perda de sincronia).** Para outros aparelhos, recomendamos utilizar o [Moonlight oficial](https://github.com/moonlight-stream/moonlight-android) ou o [Starwire padrão](https://github.com/SicarioGaming/Starwire).

---

## 🌟 O que é o Miware?

O **Miware** é um fork derivado do [Starwire](https://github.com/SicarioGaming/Starwire) (por SicarioGaming) e do [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) (por Cameron Gutman). 

Ele nasceu com um propósito único: transformar a **Xiaomi Mi Box S Gen 3** no console de streaming definitivo para jogar jogos de PC transmitidos pela rede local ou por servidores na nuvem (como instâncias Azure com GPUs NVIDIA Tesla T4) usando **Sunshine**, **Vibepollo** ou **Apollo**.

---

## 🚀 Recursos Exclusivos do Miware (O que só este APK tem)

### 1. 👻 Ghost Overlay (Modo Fantasma de Composição 60 Hz Contínua)
* **O Problema Resolvido:** No processador Amlogic da Mi Box, quando nenhum elemento de interface (UI) está sendo desenhado na tela, o driver gráfico ativa um modo de economia agressivo chamado *Bypass*. Ao fazer isso, o decodificador de vídeo passa a enviar sinais diretamente para o HDMI, quebrando a sincronia do relógio do Vulkan e causando picos de **83+ pulos de quadro (skips)** por sessão.
* **A Solução Exclusiva:** O Miware implementa o **Ghost Overlay**. Mesmo quando as informações na tela estão desligadas, o aplicativo mantém uma camada de UI ativa em segundo plano com micro-opacidade imperceptível ao olho humano (alfa de 1/255 = 0,39%). Isso obriga o *SurfaceFlinger* e o *Hardware Composer* a manterem a composição multicanal ativa a 60.00 Hz ininterruptos.
* **Resultado:** Redução comprovada de **83+ skips para 0 a 1 skip no máximo**, mantendo a tela do seu jogo 100% limpa!

### 2. ⏱️ Pacing de Quadros Calibrado para Mi Box (Zero-Skip 60 Hz)
* Pacing de quadros Vulkan sintonizado para compensar os atrasos de apresentação do driver da Mali na Mi Box S Gen 3.
* Buffer de atraso reduzido para **~7.0 ms**, garantindo resposta imediata no controle sem permitir acúmulo de pacotes no decodificador.

### 3. 🎯 Calibração Out-of-the-Box para Android TV
Ao ser instalado na Mi Box, o Miware já vem configurado de fábrica para máxima performance gamer na TV:
* Decodificador nativo por hardware: `c2.amlogic.hevc.decoder` (HEVC H.265).
* Renderizador de vídeo: **Vulkan com 10-bit SDR**.
* Nitidez gráfica: **Vulkan CAS (Contrast Adaptive Sharpening)** com suavização de pontilhado espacial (*Spatial Dithering*).
* Desativação automática de controles por toque, botões flutuantes na tela e alertas móveis desnecessários na TV.
* Modo de Desempenho Sustentado (*Sustained Performance Mode*) forçado na inicialização do stream.

### 4. 🖱️ Acumulador de Deltas Sub-pixel para Mouse
* Ao usar mouse conectado na porta USB da Mi Box, movimentos sutis não são mais descartados pelo Android. Um acumulador de deltas sub-pixel garante movimentação contínua de câmera sem congelamentos de arrancada.

### 5. 🎮 Piso de Deadzone Analógica (3%)
* Calibração de piso analógico de 3% para controles Bluetooth e dongles 2.4 GHz, eliminando ruído analógico e movimentação involuntária de mira em jogos de precisão.

---

## 🛠️ Todas as Mudanças, Correções e Melhorias

Além dos recursos exclusivos acima, o Miware traz todo o arsenal moderno do ecossistema Moonlight/Starwire:

* **ID de Pacote Próprio (`com.miware`):** Pode ser instalado lado a lado com o Moonlight oficial ou Starwire sem conflitos de assinatura ou sobrescrita.
* **Tema Visual Xiaomi Gaming:** Interface repaginada nas cores preto e laranja vibrante.
* **Gatilhos Adaptativos DualSense:** Envio de efeitos de gatilho para controles PlayStation 5 DualSense pareados por Bluetooth (quando suportado pelo host Vibepollo).
* **Passthrough USB Completo:** Compartilhamento de volantes, manetes e periféricos USB conectados na TV Box diretamente com o PC (via USB/IP encriptado).
* **Microfone In-Stream:** Retorno de voz com cancelamento de eco integrado para jogos multiplayer.

---

## ⚖️ Conformidade Legal, Licenciamento e Créditos

Este projeto é desenvolvido com total respeito às leis de direitos autorais e às licenças de código aberto. Todo o código fonte é aberto e licenciado sob a **GNU General Public License v3.0 (GPL-3.0)**.

### Licença Principal
* **Miware** é software livre: você pode redistribuí-lo e/ou modificá-lo sob os termos da **GNU General Public License v3.0**. Veja o arquivo completo em [LICENSE.txt](LICENSE.txt).

### Agradecimentos e Créditos de Projetos Anteriores:
O Miware não existiria sem a dedicação e o trabalho genial de outros desenvolvedores da comunidade de código aberto:

| Projeto | Autor Original / Mantenedor | Licença | Contribuição Utilizada |
|---|---|---|---|
| **Moonlight Android** | [Cameron Gutman (cgutman)](https://github.com/moonlight-stream/moonlight-android) | GPL-3.0 | O cliente Moonlight Android fundamental, arquitetura de streaming e decodificação. |
| **Starwire** | [SicarioGaming](https://github.com/SicarioGaming/Starwire) | GPL-3.0 | Base do projeto, passthrough USB, pipeline de testes e base do fork. |
| **LavArtemis (Artemide)** | [Lavagnou](https://github.com/ClassicOldSong/moonlight-android) | GPL-3.0 | Modo Lite de estatísticas, melhorias no renderizador e paleta de cores. |
| **Moonlight-VPlus** | [qiin2333](https://github.com/qiin2333/moonlight-vplus) | GPL-3.0 | Métodos de chamada Bluetooth para gatilhos DualSense. |
| **Moonlight-Android Fork** | [MoreOrLessSoftware](https://github.com/MoreOrLessSoftware/moonlight-android) | GPL-3.0 | Otimizações de pacer e fluxo de conexão. |
| **Apollo Microphone** | [logabell](https://github.com/logabell/apollo-microphone) | GPL-3.0 | Protocolo de áudio e captura de microfone com baixa latência. |
| **Nightjar** | [Ramazan Kara](https://github.com/RamazanKara/nightjar) | GPL-3.0 | Mapeamento de efeitos de vibração e gatilhos DualSense. |
| **Moonlight Common C** | Cameron Gutman & Diego Waxemberg | GPL-3.0 | Biblioteca nativa de baixo nível para comunicação de rede e RTP. |
| **nanors** | [sleepybishop](https://github.com/sleepybishop/nanors) | MIT | Biblioteca Reed-Solomon FEC. |
| **enet** | [cgutman / lsalzman](https://github.com/cgutman/enet) | MIT | Biblioteca de transporte UDP confiável. |

### Isenção de Marcas Registradas (Trademark Disclaimer):
* *Xiaomi*, *Mi Box*, *Android*, *Google TV*, *NVIDIA*, *GeForce*, *PlayStation*, *DualSense*, e outros nomes citados são marcas comerciais de seus respectivos proprietários. O uso destes nomes destina-se unicamente a identificar e descrever a compatibilidade técnica do hardware e software, sem qualquer afiliação, endosso ou patrocínio por parte dos detentores das marcas.

---

## 📥 Como Instalar e Usar na Mi Box S Gen 3

1. Baixe o arquivo [Miware.apk](release/Miware.apk).
2. Transfira o APK para a Mi Box via Pendrive ou pelo aplicativo *Send Files to TV*.
3. Instale o APK através de qualquer gerenciador de arquivos (como *X-plore* ou *AnExplorer*).
4. Abra o **Miware**, pareie com o seu PC (Sunshine / Vibepollo) e aproveite a transmissão com 60 Hz cravados e zero travamentos!
