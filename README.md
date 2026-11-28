# ![MiWire · Streaming de jogos no Android TV](docs/images/title.svg)

Jogue na TV usando o PC que você já tem. O computador executa o jogo, e o **MiWire** recebe o vídeo e o áudio na Box e devolve os comandos do mouse, do teclado ou do controle. Este cliente da família Moonlight foi adaptado para a **Xiaomi TV Box S de 3ª geração**, com um perfil dedicado a jogos em **1080p a 60 quadros por segundo**.

**[Baixar para a Mi Box · ARMv7a][apk-box]**　·　[Baixar o APK universal][apk-universal]　·　[Notas da versão 0.4.0][release]

## Por dentro do MiWire

| Encontre seu PC | Escolha o que jogar |
| --- | --- |
| [![Tela Computadores com o botão Adicionar por endereço][tela-pcs]][tela-pcs] | [![Biblioteca com o jogo recente, a opção Jogar e as capas dos demais títulos][tela-jogos]][tela-jogos] |
| O PC aparece na rede local. Para um computador em outra rede, use **Adicionar por endereço**. | O jogo recente fica em destaque. O direcional percorre a biblioteca e o contorno âmbar indica o foco. |

| Ajuste a transmissão | Acesse os controles durante o jogo |
| --- | --- |
| [![Configurações de transmissão com resolução, taxa de bits e ritmo de quadros][tela-ajustes]][tela-ajustes] | [![Menu da sessão com teclado, emulação de mouse e estatísticas][tela-menu]][tela-menu] |
| Resolução, taxa de bits e ritmo de quadros podem ser ajustados na própria TV. | No perfil da Box, **LB + RB** abre o menu do controle. Por ali você acessa o teclado virtual e as estatísticas da sessão. |

*Selecione uma imagem para ampliá-la. São capturas da interface em português, geradas pelos testes visuais da versão 0.4.0. O PC, as capas e a sessão usam dados de demonstração.*

## Instalação

**Escolha o arquivo pela edição que você usa.** A versão 0.4.0 tem código 326 nas duas edições. Na primeira instalação em uma Box que aceita aplicativos ARM de 32 bits, use **MiWire Box**.

| Se você usa… | Baixe este arquivo | Como fica a instalação |
| --- | --- | --- |
| MiWire Box v323, v324 ou v325 | [MiWire Box · ARMv7a][apk-box] | Atualiza a edição `com.miwire.local` com a mesma assinatura. |
| MiWire oficial, inclusive em outra arquitetura | [MiWire · universal][apk-universal] | Atualiza a edição `com.miwire`. Inclui ARMv7a, ARM64, x86 e x86-64. |
| A versão de restauração v320 | [Backup original da v320][backup] | O backup usa `com.miware`. A v326 instala ao lado dele e mantém um pareamento próprio. |

1. **No PC**, configure um servidor compatível, como [Sunshine](https://github.com/LizardByte/Sunshine), [Apollo](https://github.com/ClassicOldSong/Apollo) ou [Vibepollo](https://github.com/Nonary/Vibepollo), e disponibilize o jogo ou a área de trabalho.
2. **Na Box**, abra o APK com um gerenciador de arquivos. Se o Android solicitar, permita que esse gerenciador instale o aplicativo.
3. **No MiWire**, selecione o computador ou escolha **Adicionar por endereço**. Digite no servidor do PC o PIN que a TV exibir. Depois do pareamento, abra a biblioteca e selecione **Jogar**.

> [!IMPORTANT]
> Para conservar as configurações e o pareamento ao atualizar, instale o APK correspondente sobre a edição existente. A desinstalação apaga os dados locais. A página da versão mostra o SHA-256 de cada arquivo. [Ver arquivos e assinaturas][release]

## O perfil de 1080p60

| Recurso | Como funciona na Box |
| --- | --- |
| **Imagem** | O perfil inicial usa 1920 × 1080, 60 fps e 35 Mb/s, com HEVC (H.265), renderizador Vulkan e nitidez equilibrada. A taxa de bits pode ser ajustada durante o jogo. |
| **Codecs** | Há opções de H.264, HEVC e AV1. O formato disponível na sessão depende do que o PC consegue codificar e do que o Android anuncia para decodificação. |
| **Ritmo dos quadros** | O modo `mibox-60hz` organiza a apresentação em ciclos de tela de aproximadamente 16,67 ms. Quando o driver Vulkan fornece os tempos de apresentação, o painel mostra p50, p95, p99 e os intervalos longos observados no último minuto. |
| **Mouse e rede** | O cliente preserva movimentos fracionários e a ordem dos cliques. A recepção de vídeo trata rajadas de pacotes por Wi-Fi e Ethernet. O painel detalhado identifica a rede ativa e exibe o tempo de ida e volta quando há uma medição. |
| **Áudio e periféricos** | O receptor opcional para Windows recebe o áudio do microfone pelo protocolo criptografado SWMC v1. O compartilhamento de dispositivos USB com o PC e os gatilhos adaptativos do DualSense são experimentais. [Microfone no Windows](windows/README.md) · [USB no Windows](windows/USB-HELPER.md) |

## Equipamento de referência

| Xiaomi TV Box S · 3ª geração | Monitor AOC 22B1HM5 |
| --- | --- |
| <a href="https://www.mi.com/global/product/xiaomi-tv-box-s-3rd-gen/specs/"><img src="https://i02.appmifile.com/270_operator_sg/18/03/2025/1d8c806c228803f254a6986e86cfd749.png" width="295" alt="Xiaomi TV Box S de terceira geração, imagem oficial da Xiaomi"></a> | <a href="https://aoc.portaltpv.com.br/produto/22b1hm5-monitor-widescreen-led"><img src="https://aoc.portaltpv.com.br/uploads/images/2021/06/499-22b1hm5-monitor-widescreen-led-1624624496.png" width="220" alt="Monitor AOC 22B1HM5, imagem oficial da AOC"></a> |
| Google TV, 2 GB de RAM, 32 GB de armazenamento, HDMI 2.1 e uma porta USB 2.0. Wi-Fi 6 nas bandas de 2,4 e 5 GHz. | Painel VA de 21,5″, resolução nativa Full HD e até 75 Hz por HDMI. O perfil do projeto usa saída de **60 Hz** para a transmissão de 60 fps. |
| [Foto e especificações da Xiaomi](https://www.mi.com/global/product/xiaomi-tv-box-s-3rd-gen/specs/) · [Bandas de Wi-Fi](https://www.mi.com/uk/support/faq/details/KA-567165/) | [Foto da AOC](https://aoc.portaltpv.com.br/produto/22b1hm5-monitor-widescreen-led) · [Ficha técnica do modelo](https://aoc.portaltpv.com.br/uploads/specifications/2024/07/22b1hm5.pdf) |

Na configuração cabeada de referência, o **hub Exbom UHL-300** liga a Ethernet e os periféricos à mesma porta USB da Box. O APK da Box atende tanto ao cabo quanto ao Wi-Fi. O tema em preto OLED também funciona no painel VA do AOC.

## O que foi verificado

Na versão 0.4.0, passaram **1.043 testes funcionais** e **70 comparações visuais**. Um teste funcional foi ignorado. No emulador **Android TV 14**, a suíte registrou **142 aprovações**, dez casos ignorados e zero falhas. A abertura pelo menu da TV e a navegação com o direcional também passaram. [Compilação e testes](https://github.com/Gustavo14832/MiWire/actions/runs/46048504751) · [Relatório do Android TV](https://github.com/Gustavo14832/MiWire/actions/runs/46048504955)

> [!NOTE]
> A latência entre mexer o mouse e ver a resposta no monitor, as perdas de rede e os engasgos por minuto da v326 ainda precisam ser medidos na Box física. O histórico descreve como comparar os resultados com a v320 usando a mesma cena. [Histórico de engenharia](docs/HANDOFF_MIWIRE.md)

## Documentação e suporte

[Histórico das versões](CHANGELOG.md) · [Código e compilação](CONTRIBUTING.md) · [Origem dos binários](docs/PROVENANCE.md) · [Política de segurança](SECURITY.md)

Para relatar um problema de transmissão, informe a versão do APK, o modelo do aparelho, o codec e se estava usando Ethernet ou Wi-Fi. Uma captura do painel da sessão ajuda a localizar a etapa afetada. [Abrir um relato no GitHub](https://github.com/Gustavo14832/MiWire/issues)

## Créditos e licença

O MiWire é um projeto independente mantido por [Gustavo14832](https://github.com/Gustavo14832). A base direta é o **Starwire**, de **SicarioGaming**, que deriva do **Moonlight Android**, de **Cameron Gutman e colaboradores**. O MiWire adapta essa base à Box com o perfil de 60 Hz, ajustes de entrada e rede e uma interface de TV própria.

### Créditos ao Projeto Original e à comunidade

<details>
<summary>Ver projetos, autores e contribuições</summary>

| Projeto | Autoria | Contribuição |
| --- | --- | --- |
| **[Starwire](https://github.com/SicarioGaming/Starwire)** | SicarioGaming | Base direta, interface Cover Glow, microfone SWMC v1, USB/IP, DualSense e infraestrutura de testes. |
| **[Moonlight Android](https://github.com/moonlight-stream/moonlight-android)** | Cameron Gutman e colaboradores | Cliente Android que fornece a base de vídeo, áudio, entrada e pareamento. |
| **[Moonlight X](https://github.com/MoreOrLessSoftware/moonlight-android)** | MoreOrLessSoftware | Renderização Vulkan, integração com PyroWave, menu da sessão e ajustes por jogo. |
| **[Artemis](https://github.com/ClassicOldSong/moonlight-android)** | Yukino Song (ClassicOldSong) | Modos de toque, área de transferência, tela virtual e comandos do servidor. |
| **[Artemide](https://github.com/derflacco/moonlight-android)** | derflacco | NanoPacer, decodificação assíncrona, ajustes para MediaTek e integração de recursos do Artemis. |
| **[Moonlight de Axixi2233](https://github.com/Axixi2233/moonlight-android)** | Axixi2233 | Teclado virtual, formato dos atalhos e painel compacto de estatísticas. |
| **LavArtemis** | Lavagnou | Acessibilidade do teclado físico, indicações de desempenho ao Android e monitoramento térmico. |
| **[Moonlight V+](https://github.com/qiin2333/moonlight-vplus)** | qiin2333 e CLF (CLF184) | Exportação USB/IP, gatilhos DualSense por Bluetooth e referências para áudio háptico e estatísticas. |
| **[Moonlight X Turnip](https://github.com/NoCrypt/moonlight-x-turnip)** | ncpt (NoCrypt) | Carregamento de driver Vulkan personalizado para PyroWave. |
| **[Moonlit Artemis](https://github.com/4had45/moonlit-artemis)** | Mohammad Forhad (4had45) | Referência para alternância de monitores e zoom por gesto. |
| **[Moonlight Fold](https://github.com/sblocktechnologies/moonlight-fold)** | sblocktechnologies | Adaptação da transmissão à tela interna de aparelhos dobráveis. |
| **[Nightjar](https://github.com/RamazanKara/nightjar)** | Ramazan Kara | Encaminhamento dos efeitos de gatilho DualSense ao Android. |
| **[Apollo Microphone](https://github.com/logabell/apollo-microphone)** | logabell | Captura e transporte do microfone integrado à sessão. |
| **[VBAN e VB-CABLE](https://vb-audio.com/Cable/)** | VB-Audio (Vincent Burel) | Formato de áudio em rede e dispositivo virtual de áudio no Windows. |
| **[Moonlight Common C](https://github.com/moonlight-stream/moonlight-common-c)** | Cameron Gutman, Diego Waxemberg e colaboradores | Núcleo nativo de transmissão, recepção de pacotes e controle. |
| **[PyroWave](https://github.com/Themaister/pyrowave) e [libadrenotools](https://github.com/bylaws/libadrenotools)** | Hans-Kristian Arntzen (Themaister) e Billy Laws (bylaws) | Codec PyroWave e carregamento de drivers Vulkan para Adreno. |
| **[usbipdcpp](https://github.com/yunsmall/usbipdcpp) e [usbip-win2](https://github.com/vadimgrn/usbip-win2)** | yunsmall e vadimgrn | Servidor USB/IP (usbipdcpp, LGPLv3) e protocolo do driver para Windows. |
| **nanors e ENet** | sleepybishop e cgutman | Correção de perdas por redundância e transporte confiável sobre UDP. |
| **[Vibepollo](https://github.com/Nonary/Vibepollo), [Apollo](https://github.com/ClassicOldSong/Apollo) e [Sunshine](https://github.com/LizardByte/Sunshine)** | Chase Payne (Nonary), ClassicOldSong e LizardByte | Servidores compatíveis para o PC. |
| **[Claude Code](https://claude.com/claude-code) e [REA](https://github.com/morluto/rea)** | Anthropic e morluto | Ferramentas usadas no desenvolvimento, nos testes e na análise do projeto de origem. |

</details>

O MiWire é distribuído sob a **GNU GPL v3**. Os componentes de terceiros mantêm suas próprias licenças e avisos de autoria. As imagens de produto são da Xiaomi e da AOC. As marcas pertencem aos respectivos titulares. [Licença do MiWire](LICENSE.txt) · [Avisos dos componentes](app/src/main/assets/licenses/)

[apk-box]: https://github.com/Gustavo14832/MiWire/releases/download/v0.4.0/MiWire-Box-v326-armeabi-v7a.apk
[apk-universal]: https://github.com/Gustavo14832/MiWire/releases/download/v0.4.0/MiWire-0.4.0.apk
[release]: https://github.com/Gustavo14832/MiWire/releases/tag/v0.4.0
[backup]: https://github.com/Gustavo14832/MiWire/releases/tag/v0.3.0-miware
[tela-pcs]: app/src/test/screenshots/pcList%5BMI_BOX_1080P%5D.png
[tela-jogos]: app/src/test/screenshots/gameScreen%5BMI_BOX_1080P%5D.png
[tela-ajustes]: app/src/test/screenshots/settings%5BMI_BOX_1080P%5D.png
[tela-menu]: app/src/test/screenshots/streamMenu%5BMI_BOX_1080P%5D.png
